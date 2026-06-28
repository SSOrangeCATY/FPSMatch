package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.debug.ScopeRenderDebug;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.renderer.item.AttachmentItemRenderer;
import com.tacz.guns.util.RenderDistance;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AttachmentRender implements IFunctionalRenderer {
    private static final boolean DEBUG_ATTACHMENT_RENDER = Boolean.getBoolean("tacz.debug.attachmentRender");
    private static final Set<String> REPORTED_SKIPS = ConcurrentHashMap.newKeySet();

    private final BedrockGunModel bedrockGunModel;
    private final AttachmentType type;

    public AttachmentRender(BedrockGunModel bedrockGunModel, AttachmentType type) {
        this.bedrockGunModel = bedrockGunModel;
        this.type = type;
    }

    @Override
    public boolean usesRetainedSubmitPrepass() {
        return true;
    }

    public static void renderAttachment(ItemStack attachmentItem, ItemStack gunItem, PoseStack poseStack, ItemDisplayContext transformType, int light, int overlay) {
        poseStack.translate(0, -1.5, 0);
        if (attachmentItem.getItem() instanceof IAttachment iAttachment) {
            Identifier attachmentId = iAttachment.getAttachmentId(attachmentItem);
            TimelessAPI.getClientAttachmentIndex(attachmentId).ifPresentOrElse(attachmentIndex -> {
                BedrockAttachmentModel model = attachmentIndex.getAttachmentModel();
                Identifier texture = attachmentIndex.getModelTexture();
                // 这里是枪械里的配件渲染，没有模型材质就不渲染
                if (model != null && texture != null) {
                    // 调用低模
                    Pair<BedrockAttachmentModel, Identifier> lodModel = attachmentIndex.getLodModel();
                    // 有低模、在高模渲染范围外、不是第一人称
                    if (lodModel != null && !RenderDistance.inRenderHighPolyModelDistance(poseStack) && !transformType.firstPerson()) {
                        model = lodModel.getLeft();
                        texture = lodModel.getRight();
                    }
                    RenderType renderType = RenderTypes.entityCutout(texture);
                    model.render(attachmentItem, gunItem, poseStack, transformType, renderType, light, overlay);
                } else {
                    debugSkippedAttachment(attachmentId, "missing model or texture", model != null, texture != null, transformType);
                }
            }, () -> {
                debugSkippedAttachment(attachmentId, "missing client attachment index", false, false, transformType);
                // 没有对应的 attachmentIndex，渲染黑紫材质以提醒
                SubmitNodeCollector collector = RenderHelper.currentSubmitNodeCollector();
                if (collector != null) {
                    AttachmentItemRenderer.SLOT_ATTACHMENT_MODEL.submit(
                            poseStack, collector, RenderTypes.entityTranslucent(MissingTextureAtlasSprite.getLocation()), light, overlay);
                }
            });
        }
    }

    public static int submitMountedAttachment(ItemStack attachmentItem, ItemStack gunItem, PoseStack poseStack,
                                              ItemDisplayContext transformType, int light, int overlay) {
        SubmitNodeCollector collector = RenderHelper.currentSubmitNodeCollector();
        if (collector == null) {
            ScopeRenderDebug.path("mounted_attachment_no_collector", attachmentItem, gunItem, transformType, "");
            return BedrockGunModel.SCOPE_GUN_CLIP_NONE;
        }
        poseStack.translate(0, -1.5, 0);
        if (attachmentItem.getItem() instanceof IAttachment iAttachment) {
            Identifier attachmentId = iAttachment.getAttachmentId(attachmentItem);
            ScopeRenderDebug.path("mounted_attachment_submit", attachmentItem, gunItem, transformType, attachmentId.toString());
            var attachmentIndex = TimelessAPI.getClientAttachmentIndex(attachmentId);
            if (attachmentIndex.isEmpty()) {
                debugSkippedAttachment(attachmentId, "missing mounted client attachment index", false, false, transformType);
                AttachmentItemRenderer.SLOT_ATTACHMENT_MODEL.submit(
                        poseStack, collector, RenderTypes.entityTranslucent(MissingTextureAtlasSprite.getLocation()), light, overlay);
                return BedrockGunModel.SCOPE_GUN_CLIP_NONE;
            }
            BedrockAttachmentModel model = attachmentIndex.get().getAttachmentModel();
            Identifier texture = attachmentIndex.get().getModelTexture();
            if (model == null || texture == null) {
                ScopeRenderDebug.resolvedAttachment(attachmentItem, gunItem, transformType, attachmentIndex.get(),
                        texture, model != null, false, "missing_mounted_model_or_texture");
                debugSkippedAttachment(attachmentId, "missing mounted model or texture", model != null, texture != null, transformType);
                return BedrockGunModel.SCOPE_GUN_CLIP_NONE;
            }
            Pair<BedrockAttachmentModel, Identifier> lodModel = attachmentIndex.get().getLodModel();
            if (lodModel != null && !RenderDistance.inRenderHighPolyModelDistance(poseStack) && !transformType.firstPerson()) {
                model = lodModel.getLeft();
                texture = lodModel.getRight();
            }
            RenderType renderType = RenderTypes.entityCutout(texture);
            ScopeRenderDebug.resolvedAttachment(attachmentItem, gunItem, transformType, attachmentIndex.get(),
                    texture, true, true, "");
            return model.submitInstalled(attachmentItem, gunItem, poseStack, collector, transformType, renderType, texture, light, overlay);
        }
        return BedrockGunModel.SCOPE_GUN_CLIP_NONE;
    }

    private static void debugSkippedAttachment(Identifier attachmentId, String reason, boolean hasModel,
                                               boolean hasTexture, ItemDisplayContext transformType) {
        if (!DEBUG_ATTACHMENT_RENDER) {
            return;
        }
        String key = attachmentId + "|" + reason + "|" + transformType;
        if (REPORTED_SKIPS.add(key)) {
            GunMod.LOGGER.warn("TACZ attachment render skipped: attachment={}, reason={}, hasModel={}, hasTexture={}, transform={}",
                    attachmentId, reason, hasModel, hasTexture, transformType);
        }
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay) {
        EnumMap<AttachmentType, ItemStack> currentAttachmentItem = bedrockGunModel.getCurrentAttachmentItem();
        ItemStack attachmentItem = currentAttachmentItem.get(type);
        if (attachmentItem != null && !attachmentItem.isEmpty()) {
            Matrix3f normal = new Matrix3f(poseStack.last().normal());
            Matrix4f pose = new Matrix4f(poseStack.last().pose());
            //和枪械模型共用顶点缓冲的都需要代理到渲染结束后渲染
            bedrockGunModel.delegateRender((poseStack1, vertexBuffer1, transformType1, light1, overlay1) -> {
                PoseStack poseStack2 = new PoseStack();
                poseStack2.last().normal().mul(normal);
                poseStack2.last().pose().mul(pose);
                // 渲染配件
                renderAttachment(attachmentItem, bedrockGunModel.getCurrentGunItem(), poseStack2, transformType, light, overlay);
            });
        }
    }
}
