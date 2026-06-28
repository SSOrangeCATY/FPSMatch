package com.tacz.guns.client.init;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.client.renderer.block.GunSmithTableRenderer;
import com.tacz.guns.client.renderer.block.StatueRenderer;
import com.tacz.guns.client.renderer.block.TargetRenderer;
import com.tacz.guns.client.renderer.entity.EntityBulletRenderer;
import com.tacz.guns.client.renderer.entity.TargetMinecartRenderer;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.TargetMinecart;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(value = Dist.CLIENT)
public class ModEntitiesRender {
    @SubscribeEvent
    public static void onEntityRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        EntityRenderers.register(EntityKineticBullet.TYPE, EntityBulletRenderer::new);
        EntityRenderers.register(TargetMinecart.TYPE, TargetMinecartRenderer::new);
        BlockEntityRenderers.register(ModBlocks.GUN_SMITH_TABLE_BE.get(), GunSmithTableRenderer::new);
        BlockEntityRenderers.register(ModBlocks.TARGET_BE.get(), TargetRenderer::new);
        BlockEntityRenderers.register(ModBlocks.STATUE_BE.get(), StatueRenderer::new);
    }
}
