package revilo.createautoarm.block;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
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
import net.minecraft.world.item.*;
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
    // Slot 1: Base (Armor/Tool)
    // Slot 2: Addition (Ingot/Gem)
    // Slot 3: OUTPUT (Finished Item)
    public final SingleVariantStorage<ItemVariant>[] inventory = new SingleVariantStorage[4];
    private final Storage<ItemVariant> exposedStorage;

    // Track direction to push items out the opposite side
    private Direction lastInputSide = Direction.NORTH;

    public AutoSmithingTableBlockEntity(BlockPos pos, BlockState state) {
        super(CreateAutoArmour.SMITHING_TABLE_BE, pos, state);

        for (int i = 0; i < 4; i++) {
            int finalI = i;
            inventory[i] = new SingleVariantStorage<>() {
                @Override
                protected ItemVariant getBlankVariant() { return ItemVariant.blank(); }
                @Override
                protected long getCapacity(ItemVariant variant) { return 1; }

                @Override
                protected boolean canInsert(ItemVariant variant) {
                    // Output slot (3) is locked for insertion
                    if (finalI == 3) return false;
                    return isValidForSlot(finalI, variant.toStack());
                }

                @Override
                protected boolean canExtract(ItemVariant variant) {
                    // Only allow extracting from Output (3) to prevent stealing ingredients
                    return finalI == 3;
                }

                @Override
                protected void onFinalCommit() {
                    setChanged();
                    sendData();
                }
            };
        }
        // Expose slots for automation (Belts/Funnels)
        this.exposedStorage = new CombinedStorage<>(List.of(inventory[0], inventory[1], inventory[2], inventory[3]));
    }

    @Override
    public @Nullable Storage<ItemVariant> getItemStorage(Direction side) { return exposedStorage; }
    public Storage<ItemVariant> getStorage() { return exposedStorage; }

    /**
     * RELIABLE VALIDATION
     * Uses simple class checks instead of complex recipe lookups.
     * This fixes the "Doesn't Save" and "Can't Put Ore In" bugs.
     */
    private boolean isValidForSlot(int slot, ItemStack stack) {
        if (slot == 3) return false; // Output only

        Item item = stack.getItem();
        boolean isTemplate = item instanceof SmithingTemplateItem;
        boolean isBase = item instanceof ArmorItem
                || item instanceof TieredItem
                || item instanceof ProjectileWeaponItem;

        // Slot 0: MUST be a Template
        if (slot == 0) return isTemplate;

        // Slot 1: MUST be Armor or Tool (and not a template)
        if (slot == 1) return isBase && !isTemplate;

        // Slot 2: EVERYTHING ELSE
        // If it's not a template and not armor, we assume it's an ingredient (Ingot, Gem, etc.)
        // This stops you from putting Armor in Slot 2, but allows any Ore/Material.
        if (slot == 2) return !isTemplate && !isBase;

        return false;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Enable Belt interactions
        behaviours.add(new DirectBeltInputBehaviour(this)
                .allowingBeltFunnels()
                .setInsertionHandler(this::handleBeltInput));
    }

    // Logic for items entering via Belt
    private ItemStack handleBeltInput(TransportedItemStack transported, Direction side, boolean simulate) {
        if (!simulate && side != null) this.lastInputSide = side;
        ItemStack stack = transported.stack;

        try (Transaction t = Transaction.openOuter()) {
            // Try to insert into our filtered storage
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
        // Server-side only: Try to push output to funnels/chutes
        if (level != null && !level.isClientSide) {
            exportResult();
        }
    }

    /**
     * ACTIVE EXPORT
     * Pushes the result to connected Funnels/Belts so the table clears automatically.
     */
    private void exportResult() {
        if (inventory[3].isResourceBlank()) return;

        Direction outputSide = lastInputSide.getOpposite();

        // 1. Try Create Belt Funnel
        DirectBeltInputBehaviour behaviour = getBehaviour(DirectBeltInputBehaviour.TYPE);
        if (behaviour != null) {
            ItemStack result = inventory[3].variant.toStack((int)inventory[3].amount);

            // Pushes item into the funnel logic
            ItemStack remainder = behaviour.tryExportingToBeltFunnel(result, outputSide, false);

            if (remainder != null) {
                if (remainder.isEmpty()) {
                    inventory[3].amount = 0;
                    inventory[3].variant = ItemVariant.blank();
                } else {
                    inventory[3].amount = remainder.getCount();
                }
                notifyUpdate();
                return;
            }
        }

        // 2. Try Standard Storage (Chest/Barrel)
        BlockPos targetPos = worldPosition.relative(outputSide);
        Storage<ItemVariant> target = ItemStorage.SIDED.find(level, targetPos, outputSide.getOpposite());
        if (target != null) {
            try (Transaction t = Transaction.openOuter()) {
                long moved = StorageUtil.move(inventory[3], target, v -> true, 1, t);
                if (moved > 0) {
                    t.commit();
                    notifyUpdate();
                }
            }
        }
    }

    // Manual Interaction
    public InteractionResult onUse(Player player, InteractionHand hand, Direction side) {
        if (level == null || level.isClientSide) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);

        if (!held.isEmpty()) {
            // INSERT: Loop through inputs and find a valid home
            for (int i = 0; i < 3; i++) {
                if (inventory[i].isResourceBlank() && isValidForSlot(i, held)) {
                    inventory[i].variant = ItemVariant.of(held);
                    inventory[i].amount = 1;
                    if (!player.isCreative()) held.shrink(1);

                    if (side != null && side.getAxis().isHorizontal()) this.lastInputSide = side;
                    notifyUpdate();
                    return InteractionResult.SUCCESS;
                }
            }
        } else {
            // RETRIEVE: Take Output First
            if (!inventory[3].isResourceBlank()) {
                player.setItemInHand(hand, inventory[3].variant.toStack());
                inventory[3].variant = ItemVariant.blank();
                inventory[3].amount = 0;
                notifyUpdate();
                return InteractionResult.SUCCESS;
            }
            // Then check Inputs
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
        if (!inventory[3].isResourceBlank()) return; // Wait for output to clear

        SimpleContainer tempInv = new SimpleContainer(3);
        for (int i = 0; i < 3; i++) {
            if (inventory[i].isResourceBlank()) return;
            tempInv.setItem(i, inventory[i].variant.toStack((int) inventory[i].amount));
        }

        Optional<SmithingRecipe> match = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, tempInv, level);

        if (match.isPresent()) {
            ItemStack result = match.get().assemble(tempInv, level.registryAccess());

            // Consume Ingredients
            for (int i = 0; i < 3; i++) {
                inventory[i].amount = 0;
                inventory[i].variant = ItemVariant.blank();
            }

            // Set Result
            inventory[3].variant = ItemVariant.of(result);
            inventory[3].amount = 1;

            notifyUpdate();
            level.levelEvent(1044, worldPosition, 0);
        }
    }

    public int getFilledSlots() {
        int count = 0;
        for (int i = 0; i < 3; i++) { if(!inventory[i].isResourceBlank()) count++; }
        return count;
    }

    // Used by PressingBehaviourMixin
    public boolean isOutputEmpty() {
        return inventory[3].isResourceBlank();
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        if (compound.contains("LastInputSide")) lastInputSide = Direction.from3DDataValue(compound.getInt("LastInputSide"));
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
        if (compound.contains("LastInputSide")) lastInputSide = Direction.from3DDataValue(compound.getInt("LastInputSide"));
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