package revilo.createautoarm.block;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import revilo.createautoarm.CreateAutoArmour;

import java.util.List;
import java.util.Optional;

@SuppressWarnings({"rawtypes", "unchecked", "UnstableApiUsage"})
public class AutoSmithingTableBlockEntity extends SmartBlockEntity implements SidedStorageBlockEntity {

    // Slot 0: Template
    // Slot 1: Base (Tool/Armor)
    // Slot 2: Addition (Ingot/Gem)
    // Slot 3: OUTPUT (Finished Item)
    public final SingleVariantStorage<ItemVariant>[] inventory = new SingleVariantStorage[4];
    private final Storage<ItemVariant> exposedStorage;

    public AutoSmithingTableBlockEntity(BlockPos pos, BlockState state) {
        super(CreateAutoArmour.SMITHING_TABLE_BE, pos, state);

        for (int i = 0; i < 4; i++) {
            int finalI = i;
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
                protected boolean canInsert(ItemVariant variant) {
                    // Only allow insertion into Input Slots (0, 1, 2)
                    if (finalI == 3) return false;
                    return isValidForSlot(finalI, variant.toStack());
                }

                @Override
                protected boolean canExtract(ItemVariant variant) {
                    // Only allow extraction from Output Slot (3)
                    return finalI == 3;
                }

                @Override
                protected void onFinalCommit() {
                    setChanged();
                    sendData();
                }
            };
        }
        this.exposedStorage = new CombinedStorage<>(List.of(inventory[0], inventory[1], inventory[2], inventory[3]));
    }

    @Override
    public @Nullable Storage<ItemVariant> getItemStorage(Direction side) {
        return exposedStorage;
    }

    public Storage<ItemVariant> getStorage() {
        return exposedStorage;
    }

    /**
     * ROBUST FILTER:
     * Slot 0: Templates
     * Slot 1: Armor/Tools
     * Slot 2: Everything Else (Ensures Modded Ores/Ingots work)
     * Slot 3: Output Only (No insert)
     */
    private boolean isValidForSlot(int slot, ItemStack stack) {
        if (slot == 3) return false; // Output only

        boolean isTemplate = stack.getItem() instanceof SmithingTemplateItem;
        boolean isBase = stack.getItem() instanceof ArmorItem
                || stack.getItem() instanceof TieredItem
                || stack.isDamageableItem();

        if (slot == 0) return isTemplate;
        if (slot == 1) return isBase && !isTemplate;

        // Slot 2: Catch-all for Ingredients.
        // We removed the strict recipe check because it was blocking valid ores.
        // Now it accepts anything that ISN'T a Template and ISN'T Armor.
        if (slot == 2) return !isTemplate && !isBase;

        return false;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this)
                .allowingBeltFunnels()
                .setInsertionHandler(this::handleBeltInput));
    }

    private ItemStack handleBeltInput(TransportedItemStack transported, Direction side, boolean simulate) {
        ItemStack stack = transported.stack;
        try (Transaction t = Transaction.openOuter()) {
            long inserted = exposedStorage.insert(ItemVariant.of(stack), stack.getCount(), t);
            if (!simulate) t.commit();
            if (inserted == stack.getCount()) return ItemStack.EMPTY;
            ItemStack remainder = stack.copy();
            remainder.shrink((int) inserted);
            return remainder;
        }
    }

    @Override
    public void tick() {
        super.tick();
    }

    public InteractionResult onUse(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);

        // 1. Manual Insert (Inputs 0-2)
        if (!held.isEmpty()) {
            for (int i = 0; i < 3; i++) {
                if (inventory[i].isResourceBlank() && isValidForSlot(i, held)) {
                    inventory[i].variant = ItemVariant.of(held);
                    inventory[i].amount = 1;
                    if (!player.isCreative()) held.shrink(1);
                    notifyUpdate();
                    return InteractionResult.SUCCESS;
                }
            }
        }
        // 2. Manual Take (From Output 3 first, then Inputs)
        else {
            if (!inventory[3].isResourceBlank()) {
                player.setItemInHand(hand, inventory[3].variant.toStack());
                inventory[3].variant = ItemVariant.blank();
                inventory[3].amount = 0;
                notifyUpdate();
                return InteractionResult.SUCCESS;
            }
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

        if (!inventory[3].isResourceBlank()) return;

        SimpleContainer tempInv = new SimpleContainer(3);
        for (int i = 0; i < 3; i++) {
            if (inventory[i].isResourceBlank()) return;
            tempInv.setItem(i, inventory[i].variant.toStack((int) inventory[i].amount));
        }

        Optional<SmithingRecipe> match = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, tempInv, level);

        if (match.isPresent()) {
            ItemStack result = match.get().assemble(tempInv, level.registryAccess());

            for (int i = 0; i < 3; i++) {
                inventory[i].amount = 0;
                inventory[i].variant = ItemVariant.blank();
            }

            // Output to Slot 3
            inventory[3].variant = ItemVariant.of(result);
            inventory[3].amount = 1;

            notifyUpdate();
            level.levelEvent(1044, worldPosition, 0);
        }
    }

    public int getFilledSlots() {
        int count = 0;
        for (int i = 0; i < 3; i++) {
            if(!inventory[i].isResourceBlank()) count++;
        }
        return count;
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        for (int i = 0; i < 4; i++) {
            CompoundTag tag = new CompoundTag();
            tag.put("variant", inventory[i].variant.toNbt());
            tag.putLong("amount", inventory[i].amount);
            compound.put("Slot" + i, tag);
        }
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        for (int i = 0; i < 4; i++) {
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