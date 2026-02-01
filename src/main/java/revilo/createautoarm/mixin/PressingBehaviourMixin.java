package revilo.createautoarm.mixin;

import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revilo.createautoarm.block.AutoSmithingTableBlockEntity;

@Mixin(PressingBehaviour.class)
public abstract class PressingBehaviourMixin extends BlockEntityBehaviour {

    @Shadow public boolean running;
    @Shadow public PressingBehaviour.Mode mode;

    public PressingBehaviourMixin() {
        super(null);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void autoSmithingTrigger(CallbackInfo ci) {
        if (getWorld() == null || getWorld().isClientSide) return;

        // If already running, do nothing
        if (running) return;

        // Ensure we are attached to a Mechanical Press
        if (blockEntity instanceof MechanicalPressBlockEntity press) {

            // Must have rotational speed to work
            if (Math.abs(press.getSpeed()) == 0) return;

            // Check 2 blocks below (Standard Press gap)
            // [Press] (Y)
            // [Air]   (Y-1)
            // [Table] (Y-2)
            BlockPos targetPos = getPos().below(2);
            BlockEntity targetBE = getWorld().getBlockEntity(targetPos);

            if (targetBE instanceof AutoSmithingTableBlockEntity table) {
                // If the table is full (3 items), force start the press
                if (table.getFilledSlots() == 3) {
                    running = true;
                    mode = PressingBehaviour.Mode.BELT; // Uses BELT mode logic which is suitable for "in-place" processing
                    blockEntity.sendData();
                }
            }
        }
    }
}