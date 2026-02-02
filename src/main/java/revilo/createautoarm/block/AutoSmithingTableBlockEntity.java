package revilo.createautoarm.block;

import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import revilo.createautoarm.CreateAutoArmour;

import java.util.List;
import java.util.Optional;

@SuppressWarnings({"rawtypes", "unchecked", "UnstableApiUsage"})
public class AutoSmithingTableBlockEntity extends SmartBlockEntity {

    // Slot 0: Template, Slot 1: Base, Slot 2: Addition
    public final SingleVariantStorage<ItemVariant>[] inventory = new SingleVariantStorage[3];
    private final Storage<ItemVariant> exposedStorage;

    public AutoSmithingTableBlockEntity(BlockPos pos, BlockState state) {
        super(CreateAutoArmour.SMITHING_TABLE_BE, pos, state);

        for (int i = 0; i < 3; i++) {
            inventory[i] = new SingleVariantStorage<>() {
                @Override
                protected ItemVariant getBlankVariant() {
                    return ItemVariant.blank();
                }

                @Override
                protected long getCapacity(ItemVariant variant) {
                    return 1;
                }

                @Override
                protected void onFinalCommit() {
                    setChanged();
                    sendData();
                }
            };
        }
        this.exposedStorage = new CombinedStorage<>(List.of(inventory[0], inventory[1], inventory[2]));
    }

    public Storage<ItemVariant> getStorage() {
        return exposedStorage;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // No behaviours needed
    }

    @Override
    public void tick() {
        super.tick();
        // We moved the pressing logic to the Mixin for reliability,
        // but keeping this empty override ensures standard SmartBlockEntity behavior
    }

    public InteractionResult onUse(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);

        // 1. Try to INSERT
        if (!held.isEmpty()) {
            // Simple Insert: Put it in the first available slot
            for (int i = 0; i < 3; i++) {
                if (inventory[i].isResourceBlank()) {
                    inventory[i].variant = ItemVariant.of(held);
                    inventory[i].amount = 1;
                    if (!player.isCreative()) held.shrink(1);
                    notifyUpdate();
                    return InteractionResult.SUCCESS;
                }
            }
        }
        // 2. Try to TAKE item (LIFO)
        else {
            for (int i = 2; i >= 0; i--) {
                if (!inventory[i].isResourceBlank()) {
                    player.setItemInHand(hand, inventory[i].variant.toStack());
                    inventory[i].variant = ItemVariant.blank();
                    inventory[i].amount = 0;
                    notifyUpdate();
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.PASS;
    }

    public void attemptCraft() {
        if (level == null) return;

        SimpleContainer tempInv = new SimpleContainer(3);
        for (int i = 0; i < 3; i++) {
            if (inventory[i].isResourceBlank()) return;
            tempInv.setItem(i, inventory[i].variant.toStack((int) inventory[i].amount));
        }

        Optional<SmithingRecipe> match = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, tempInv, level);

        if (match.isPresent()) {
            ItemStack result = match.get().assemble(tempInv, level.registryAccess());

            // Consume inputs
            for (SingleVariantStorage<ItemVariant> slot : inventory) {
                slot.amount = 0;
                slot.variant = ItemVariant.blank();
            }

            // Output result to first slot
            inventory[0].variant = ItemVariant.of(result);
            inventory[0].amount = 1;

            notifyUpdate();
            level.levelEvent(1044, worldPosition, 0);
        }
    }

    // THIS is the method you were missing that caused the compile error
    public int getFilledSlots() {
        int count = 0;
        for(SingleVariantStorage<ItemVariant> slot : inventory) {
            if(!slot.isResourceBlank()) count++;
        }
        return count;
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        for (int i = 0; i < 3; i++) {
            CompoundTag tag = new CompoundTag();
            tag.put("variant", inventory[i].variant.toNbt());
            tag.putLong("amount", inventory[i].amount);
            compound.put("Slot" + i, tag);
        }
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        for (int i = 0; i < 3; i++) {
            if (compound.contains("Slot" + i)) {
                CompoundTag tag = compound.getCompound("Slot" + i);
                if (tag.contains("variant")) {
                    inventory[i].variant = ItemVariant.fromNbt(tag.getCompound("variant"));
                    inventory[i].amount = tag.getLong("amount");
                }
            }
        }
    }
}