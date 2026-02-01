package revilo.createautoarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
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

        long time = be.getLevel().getGameTime();
        float angle = (time + partialTicks) * 2;

        float[][] offsets = {
                {0.5f, 0.25f},  // Top Center (Template)
                {0.75f, 0.75f}, // Bottom Right (Base)
                {0.25f, 0.75f}  // Bottom Left (Addition)
        };

        for (int i = 0; i < 3; i++) {
            if (be.inventory[i].isResourceBlank()) continue;

            ItemStack stack = be.inventory[i].variant.toStack();
            ms.pushPose();
            ms.translate(offsets[i][0], 1.05, offsets[i][1]); // Moved up slightly to prevent z-fighting
            ms.scale(0.5f, 0.5f, 0.5f); // Slightly larger
            ms.mulPose(Axis.YP.rotationDegrees(angle));

            // FIXED mode usually has better lighting for flat surfaces
            // We use the block's light level (passed in 'light')
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);

            ms.popPose();
        }
    }
}