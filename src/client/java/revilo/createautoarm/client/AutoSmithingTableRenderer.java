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

        float[][] offsets = {
                {0.5f, 0.25f},  // Slot 0: Top Center (Template)
                {0.75f, 0.75f}, // Slot 1: Bottom Right (Base)
                {0.25f, 0.75f}, // Slot 2: Bottom Left (Addition)
                {0.5f, 0.5f}    // Slot 3: Center (Output)
        };

        // Force full brightness so items aren't dark
        int brightLight = LightTexture.pack(15, 15);

        for (int i = 0; i < 4; i++) {
            if (be.inventory[i].isResourceBlank()) continue;

            ItemStack stack = be.inventory[i].variant.toStack();
            ms.pushPose();

            // Lowered back to normal height (slightly above table at 1.02)
            double yOffset = 1.02;

            // Output item sits slightly higher
            if (i == 3) yOffset = 1.05;

            ms.translate(offsets[i][0], yOffset, offsets[i][1]);
            ms.mulPose(Axis.XP.rotationDegrees(90));

            float scale = 0.35f;
            if (i == 3) scale = 0.45f;
            ms.scale(scale, scale, scale);

            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, brightLight, overlay, ms, buffer, be.getLevel(), 0);

            ms.popPose();
        }
    }
}