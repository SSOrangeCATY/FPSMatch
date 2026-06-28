package com.tacz.guns.client.event;

import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class RenderHeadShotAABB {
    private static final Identifier ENTITY_HITBOXES_DEBUG_ENTRY = Identifier.withDefaultNamespace("entity_hitboxes");

    @SubscribeEvent
    public static void onRenderEntity(RenderLivingEvent.Post<?, ?, ?> event) {
        boolean canRender = Minecraft.getInstance().debugEntries.isCurrentlyEnabled(ENTITY_HITBOXES_DEBUG_ENTRY);
        if (!canRender) {
            return;
        }
        if (!RenderConfig.HEAD_SHOT_DEBUG_HITBOX.get()) {
            return;
        }
        LivingEntityRenderState state = event.getRenderState();
        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(state.entityType);
        if (entityId == null) {
            return;
        }
        AABB aabb = HeadShotAABBConfigRead.getAABB(entityId);
        if (aabb == null) {
            float width = state.boundingBoxWidth;
            float eyeHeight = state.eyeHeight;
            aabb = new AABB(-width / 2, eyeHeight - 0.25, -width / 2, width / 2, eyeHeight + 0.25, width / 2).inflate(0.01);
        }
        AABB renderAabb = aabb;
        event.getSubmitNodeCollector().submitShapeOutline(event.getPoseStack(), Shapes.create(renderAabb), RenderTypes.lines(), 0xFFFFFF00, 1.0F, false);
    }
}
