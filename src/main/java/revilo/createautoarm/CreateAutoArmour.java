package revilo.createautoarm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import revilo.createautoarm.block.AutoSmithingTableBlockEntity;

@SuppressWarnings("UnstableApiUsage")
public class CreateAutoArmour implements ModInitializer {
    public static final String MOD_ID = "create-auto-armour";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static BlockEntityType<AutoSmithingTableBlockEntity> SMITHING_TABLE_BE;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Create Auto Armour");

        SMITHING_TABLE_BE = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                new ResourceLocation(MOD_ID, "smithing_table"),
                FabricBlockEntityTypeBuilder.create(AutoSmithingTableBlockEntity::new, Blocks.SMITHING_TABLE).build()
        );

        // Register Storage so Belts/Funnels can insert items
        ItemStorage.SIDED.registerForBlockEntity((be, dir) -> be.getStorage(), SMITHING_TABLE_BE);
    }
}