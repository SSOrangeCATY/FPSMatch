package com.tacz.guns;

import com.tacz.guns.api.resource.ResourceManager;
import com.tacz.guns.config.ClientConfig;
import com.tacz.guns.config.CommonConfig;
import com.tacz.guns.config.ServerConfig;
import com.tacz.guns.init.*;
import com.tacz.guns.init.ModContainer;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.server.packs.PackType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(GunMod.MOD_ID)
public class GunMod {
    public static final String MOD_ID = "tacz";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    /**
     * 默认模型包文件夹
     */
    public static final String DEFAULT_GUN_PACK_NAME = "tacz_default_gun";

    public GunMod(IEventBus bus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.init());
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.init());
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.init());

        Dist side = FMLEnvironment.getDist();
        GunPackLoader.INSTANCE.packType = side.isClient() ? PackType.CLIENT_RESOURCES : PackType.SERVER_DATA;

        ModBlocks.BLOCKS.register(bus);
        ModBlocks.TILE_ENTITIES.register(bus);
        ModAttachments.ATTACHMENT_TYPES.register(bus);
        ModCreativeTabs.TABS.register(bus);
        ModItems.ITEMS.register(bus);
        ModEntities.ENTITY_TYPES.register(bus);
        ModRecipe.RECIPE_SERIALIZERS.register(bus);
        ModRecipe.RECIPE_TYPES.register(bus);
        ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(bus);
        ModContainer.CONTAINER_TYPE.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModParticles.PARTICLE_TYPES.register(bus);
        ModAttributes.ATTRIBUTES.register(bus);
        registerDefaultExtraGunPack();
        AttachmentPropertyManager.registerModifier();
    }

    private static void registerDefaultExtraGunPack() {
        String jarDefaultPackPath = String.format("/assets/%s/custom/%s", GunMod.MOD_ID, DEFAULT_GUN_PACK_NAME);
        ResourceManager.registerExportResource(GunMod.class, jarDefaultPackPath);
    }
}
