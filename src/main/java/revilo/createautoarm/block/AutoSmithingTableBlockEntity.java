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
        // Expose all 3 slots as one big inventory for belts to insert into
        this.exposedStorage = new CombinedStorage<>(List.of(inventory[0], inventory[1], inventory[2]));
    }

    public Storage<ItemVariant> getStorage() {
        return exposedStorage;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // No behaviours needed, we handle logic manually
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        // Custom logic to detect if a Press is coming down on us
        // Note: The Press requires a Redstone Signal to activate since we don't have a Create Recipe
        BlockEntity beAbove = level.getBlockEntity(worldPosition.above(2));
        if (beAbove instanceof MechanicalPressBlockEntity press) {
            checkPressing(press);
        } else {
            // Sometimes the press is physically in the block directly above (when extended)
            beAbove = level.getBlockEntity(worldPosition.above());
            if (beAbove instanceof MechanicalPressBlockEntity press) {
                checkPressing(press);
            }
        }
    }

    private void checkPressing(MechanicalPressBlockEntity press) {
        PressingBehaviour pressingBehaviour = press.getPressingBehaviour();
        if (pressingBehaviour == null) return;

        // 0 = Retracted, 1 = Fully Extended (Hitting us)
        float progress = pressingBehaviour.getRenderedHeadOffset(0);

        // If the press is extended enough to hit the table
        if (progress > 0.5f && !inventory[0].isResourceBlank() && !inventory[1].isResourceBlank() && !inventory[2].isResourceBlank()) {
            attemptCraft();
        }
    }

    public InteractionResult onUse(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);

        // 1. Try to insert item
        if (!held.isEmpty()) {
            for (SingleVariantStorage<ItemVariant> slot : inventory) {
                if (slot.isResourceBlank()) {
                    slot.variant = ItemVariant.of(held);
                    slot.amount = 1;
                    if (!player.isCreative()) held.shrink(1);
                    notifyUpdate();
                    return InteractionResult.SUCCESS; // We handled it, don't open GUI
                }
            }
        }
        // 2. Try to take item (LIFO)
        else {
            for (int i = 2; i >= 0; i--) {
                if (!inventory[i].isResourceBlank()) {
                    player.setItemInHand(hand, inventory[i].variant.toStack());
                    inventory[i].variant = ItemVariant.blank();
                    inventory[i].amount = 0;
                    notifyUpdate();
                    return InteractionResult.SUCCESS; // We handled it
                }
            }
        }

        // 3. If we couldn't insert or take anything, return PASS so vanilla GUI opens
        return InteractionResult.PASS;
    }

    public void attemptCraft() {
        if (level == null) return;

        SimpleContainer tempInv = new SimpleContainer(3);
        for (int i = 0; i < 3; i++) {
            if (inventory[i].isResourceBlank()) return; // Not full
            tempInv.setItem(i, inventory[i].variant.toStack((int) inventory[i].amount));
        }

        Optional<SmithingRecipe> match = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, tempInv, level);

        if (match.isPresent()) {
            ItemStack result = match.get().assemble(tempInv, level.registryAccess());

            for (SingleVariantStorage<ItemVariant> slot : inventory) {
                slot.amount = 0;
                slot.variant = ItemVariant.blank();
            }

            // Output result to first slot
            inventory[0].variant = ItemVariant.of(result);
            inventory[0].amount = 1;

            notifyUpdate();
            level.levelEvent(1044, worldPosition, 0); // Sound
        }
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