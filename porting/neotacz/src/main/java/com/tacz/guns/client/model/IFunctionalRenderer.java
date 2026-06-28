package com.tacz.guns.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.item.ItemDisplayContext;

public interface IFunctionalRenderer {
    void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay);

    /**
     * Retained submit effects are collected before the gun body's custom geometry callback.
     * True model geometry must keep rendering in the callback so functional visibility nodes
     * preserve the old immediate-mode semantics.
     */
    default boolean usesRetainedSubmitPrepass() {
        return false;
    }
}
