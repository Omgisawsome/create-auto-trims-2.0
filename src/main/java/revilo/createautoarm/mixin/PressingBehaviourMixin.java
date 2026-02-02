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

    public PressingBehaviourMixin() {
        super(null);
    }

    // 1. TRIGGER: Force start the press if table is full
    @Inject(method = "tick", at = @At("HEAD"))
    private void autoSmithingTrigger(CallbackInfo ci) {
        if (getWorld() == null || getWorld().isClientSide) return;

        if (!running && blockEntity instanceof MechanicalPressBlockEntity press) {
            if (Math.abs(press.getSpeed()) == 0) return;

            BlockPos targetPos = getPos().below(2);
            BlockEntity targetBE = getWorld().getBlockEntity(targetPos);

            if (targetBE instanceof AutoSmithingTableBlockEntity table) {
                if (table.getFilledSlots() == 3) {
                    running = true;
                    // Note: We do NOT force Mode.BELT anymore to avoid state locking
                    blockEntity.sendData();
                }
            }
        }
    }

    // 2. CRAFT: Force the table to craft when the press actually hits it
    @Inject(method = "tick", at = @At("TAIL"))
    private void autoSmithingCraft(CallbackInfo ci) {
        if (getWorld() == null || getWorld().isClientSide) return;

        if (running && blockEntity instanceof MechanicalPressBlockEntity press) {
            float progress = ((PressingBehaviour)(Object)this).getRenderedHeadOffset(0);

            // When press head is fully extended (approx > 0.95)
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