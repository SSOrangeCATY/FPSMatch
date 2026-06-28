package com.tacz.guns.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.renderer.BedrockSubmitUtils;
import com.tacz.guns.client.renderer.block.GunSmithTableRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

public class GunSmithTableItemRenderer {
    private static final SlotModel SLOT_BLOCK_MODEL = new SlotModel();

    public void submitByItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext transformType, @Nonnull PoseStack poseStack,
                             @Nonnull SubmitNodeCollector submitNodeCollector, int pPackedLight, int pPackedOverlay) {
        poseStack.pushPose();
        GunSmithTableRenderer.getIndex(stack).ifPresentOrElse(index -> {
            BedrockModel model = index.getModel();
            Identifier texture = index.getTexture();
            if (model == null || texture == null) {
                return;
            }

            ItemTransforms transforms = index.getTransforms();
            if (transforms != null) {
                poseStack.translate(0.5F, 0.5F, 0.5F);
                ItemTransform transform = transforms.getTransform(transformType);
                transform.apply(false, poseStack.last());
                poseStack.translate(-0.5F, -0.5F, -0.5F);
            }

            poseStack.translate(0.5, 1.5, 0.5);
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));
            RenderType renderType = RenderTypes.entityTranslucent(texture);
            BedrockSubmitUtils.submitModel(submitNodeCollector, poseStack, renderType, model, transformType, pPackedLight, pPackedOverlay);
        }, () -> submitSlotTexture(poseStack, submitNodeCollector, pPackedLight, pPackedOverlay, MissingTextureAtlasSprite.getLocation()));
        poseStack.popPose();
    }

    private static void submitSlotTexture(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int pPackedLight, int pPackedOverlay, Identifier texture) {
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.ZN.rotationDegrees(180));
        SLOT_BLOCK_MODEL.submit(poseStack, submitNodeCollector, RenderTypes.entityTranslucent(texture), pPackedLight, pPackedOverlay);
    }
}
