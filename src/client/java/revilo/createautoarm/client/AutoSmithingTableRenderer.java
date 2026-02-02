package revilo.createautoarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import revilo.createautoarm.block.AutoSmithingTableBlockEntity;

public class AutoSmithingTableRenderer extends SmartBlockEntityRenderer<AutoSmithingTableBlockEntity> {

    public AutoSmithingTableRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(AutoSmithingTableBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (be.getLevel() == null) return;

        // Position offsets for the 3 slots (Triangle formation)
        float[][] offsets = {
                {0.5f, 0.25f},  // Top Center (Template)
                {0.75f, 0.75f}, // Bottom Right (Base)
                {0.25f, 0.75f}  // Bottom Left (Addition)
        };

        for (int i = 0; i < 3; i++) {
            if (be.inventory[i].isResourceBlank()) continue;

            ItemStack stack = be.inventory[i].variant.toStack();
            ms.pushPose();

            // Position items to sit flat on top of the block
            ms.translate(offsets[i][0], 1.015, offsets[i][1]);

            // Lie flat (Rotate 90 degrees around X axis)
            ms.mulPose(Axis.XP.rotationDegrees(90));

            // Scale down to be smaller (Depot style)
            ms.scale(0.35f, 0.35f, 0.35f);

            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);

            ms.popPose();
        }
    }
}