package revilo.createautoarm;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import revilo.createautoarm.client.AutoSmithingTableRenderer;

public class CreateAutoArmourClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(CreateAutoArmour.SMITHING_TABLE_BE, AutoSmithingTableRenderer::new);
    }
}