package revilo.createautoarm.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revilo.createautoarm.block.AutoSmithingTableBlockEntity;

@Mixin(SmithingTableBlock.class)
public class SmithingTableMixin implements EntityBlock {

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutoSmithingTableBlockEntity(pos, state);
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AutoSmithingTableBlockEntity smithingBe) {
            // Attempt custom interaction
            InteractionResult result = smithingBe.onUse(player, hand);

            // If SUCCESS, we handled it -> cancel vanilla.
            // If PASS, we didn't do anything -> let vanilla open the GUI.
            if (result == InteractionResult.SUCCESS) {
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }
}