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
    @Shadow public int runningTicks;
    @Shadow public PressingBehaviour.Mode mode;

    public PressingBehaviourMixin() {
        super(null);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void autoSmithingTrigger(CallbackInfo ci) {
        if (getWorld() == null || getWorld().isClientSide) return;

        // Only start if not currently running
        if (!running && blockEntity instanceof MechanicalPressBlockEntity press) {
            if (Math.abs(press.getSpeed()) == 0) return;

            BlockPos targetPos = getPos().below(2);
            BlockEntity targetBE = getWorld().getBlockEntity(targetPos);

            if (targetBE instanceof AutoSmithingTableBlockEntity table) {
                // Ensure inputs are full AND output is empty (so we don't press for no reason)
                if (table.getFilledSlots() == 3 && table.isOutputEmpty()) {

                    // Force Start & Reset State
                    running = true;
                    runningTicks = 0;
                    mode = PressingBehaviour.Mode.WORLD; // Force WORLD mode for consistency

                    blockEntity.sendData();
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void autoSmithingCraft(CallbackInfo ci) {
        if (getWorld() == null || getWorld().isClientSide) return;

        if (running && blockEntity instanceof MechanicalPressBlockEntity press) {
            float progress = ((PressingBehaviour)(Object)this).getRenderedHeadOffset(0);

            // Trigger at bottom of animation
            if (progress > 0.95f) {
                BlockPos targetPos = getPos().below(2);
                BlockEntity targetBE = getWorld().getBlockEntity(targetPos);

                if (targetBE instanceof AutoSmithingTableBlockEntity table) {
                    table.attemptCraft();
                }
            }
        }
    }
}