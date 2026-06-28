package com.tacz.guns.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.renderer.BedrockSubmitUtils;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.util.RenderDistance;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

public class AttachmentItemRenderer {
    public static final SlotModel SLOT_ATTACHMENT_MODEL = new SlotModel();

    public void submitByItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext transformType, @Nonnull PoseStack poseStack,
                             @Nonnull SubmitNodeCollector submitNodeCollector, int pPackedLight, int pPackedOverlay) {
        if (stack.getItem() instanceof IAttachment iAttachment) {
            Identifier attachmentId = iAttachment.getAttachmentId(stack);
            poseStack.pushPose();
            TimelessAPI.getClientAttachmentIndex(attachmentId).ifPresentOrElse(attachmentIndex -> {
                if (transformType == ItemDisplayContext.GUI) {
                    submitSlotTexture(poseStack, submitNodeCollector, pPackedLight, pPackedOverlay, attachmentIndex.getSlotTexture());
                    return;
                }
                poseStack.translate(0.5, 2, 0.5);
                poseStack.scale(-1, -1, 1);
                if (transformType == ItemDisplayContext.FIXED) {
                    poseStack.mulPose(Axis.YN.rotationDegrees(90f));
                }
                this.submitDefaultAttachment(transformType, poseStack, submitNodeCollector, pPackedLight, pPackedOverlay, attachmentIndex);
            }, () -> submitSlotTexture(poseStack, submitNodeCollector, pPackedLight, pPackedOverlay, MissingTextureAtlasSprite.getLocation()));
            poseStack.popPose();
        }
    }

    private void submitDefaultAttachment(@NotNull ItemDisplayContext transformType, @NotNull PoseStack poseStack, @NotNull SubmitNodeCollector submitNodeCollector, int pPackedLight, int pPackedOverlay, ClientAttachmentIndex attachmentIndex) {
        BedrockAttachmentModel model = attachmentIndex.getAttachmentModel();
        Identifier texture = attachmentIndex.getModelTexture();
        if (model != null && texture != null) {
            Pair<BedrockAttachmentModel, Identifier> lodModel = attachmentIndex.getLodModel();
            if (lodModel != null && !RenderDistance.inRenderHighPolyModelDistance(poseStack) && !transformType.firstPerson()) {
                model = lodModel.getLeft();
                texture = lodModel.getRight();
            }
            RenderType renderType = RenderTypes.entityCutout(texture);
            BedrockSubmitUtils.submitModel(submitNodeCollector, poseStack, renderType, model, transformType, pPackedLight, pPackedOverlay);
        } else {
            poseStack.translate(0, 0.5, 0);
            if (transformType == ItemDisplayContext.FIXED) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            }
            SLOT_ATTACHMENT_MODEL.submit(poseStack, submitNodeCollector, RenderTypes.entityTranslucent(attachmentIndex.getSlotTexture()), pPackedLight, pPackedOverlay);
        }
    }

    private static void submitSlotTexture(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int pPackedLight, int pPackedOverlay, Identifier texture) {
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.ZN.rotationDegrees(180));
        SLOT_ATTACHMENT_MODEL.submit(poseStack, submitNodeCollector, RenderTypes.entityTranslucent(texture), pPackedLight, pPackedOverlay);
    }
}
