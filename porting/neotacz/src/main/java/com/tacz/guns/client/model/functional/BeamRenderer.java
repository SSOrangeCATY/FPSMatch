package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.BedrockSubmitUtils;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.LaserColorUtil;
import net.minecraft.util.LightCoordsUtil;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;

public class BeamRenderer  {
    public static final Identifier LASER_BEAM_TEXTURE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/entity/beam.png");
    private static final LaserConfig DEFAULT_LASER_CONFIG = new LaserConfig();

    public static void renderLaserBeam(ItemStack stack, PoseStack poseStack, ItemDisplayContext transformType, @Nonnull List<BedrockPart> path) {
        if (stack == null || !transformType.firstPerson() && !(transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
            return;
        }

		if (ARCompat.shouldAccelerate() && renderLaserBeamAccelerated(stack, poseStack, transformType, path)) {
			return;
		}

        SubmitNodeCollector collector = RenderHelper.currentSubmitNodeCollector();
        if (collector == null) {
            return;
        }
        poseStack.pushPose();
        {
            for (int i = 0; i < path.size(); ++i) {
                path.get(i).translateAndRotateAndScale(poseStack);
            }

            LaserConfig laserConfig = getLaserConfig(stack);

            int color = LaserColorUtil.getLaserColor(stack, laserConfig);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            float z = transformType.firstPerson() ? -laserConfig.getLength() : -laserConfig.getLengthThird();
            float width = transformType.firstPerson() ? laserConfig.getWidth() : laserConfig.getWidthThird();
            boolean fadeOut = RenderConfig.ENABLE_LASER_FADE_OUT.get();
            RenderHelper.submitCustomGeometry(collector, poseStack, LaserBeamRenderState.getLaserBeam(), (pose, builder) ->
                    stringVertex(z, width, builder, BedrockSubmitUtils.fromPose(pose).last(), r, g, b, fadeOut));
        }
        poseStack.popPose();
    }

	public static boolean renderLaserBeamAccelerated(ItemStack stack, PoseStack poseStack, ItemDisplayContext transformType, @Nonnull List<BedrockPart> path) {
		return false;
	}

    private static LaserConfig getLaserConfig(ItemStack stack) {
        if (stack == null) {
            return DEFAULT_LASER_CONFIG;
        }

        if (stack.getItem() instanceof IAttachment iAttachment) {
            return TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(stack))
                    .map(ClientAttachmentIndex::getLaserConfig)
                    .orElse(DEFAULT_LASER_CONFIG);
        }

        if (stack.getItem() instanceof IGun) {
            return TimelessAPI.getGunDisplay(stack)
                    .map(GunDisplayInstance::getLaserConfig)
                    .orElse(DEFAULT_LASER_CONFIG);
        }

        return DEFAULT_LASER_CONFIG;
    }

    private static void stringVertex(float z, float width, VertexConsumer pConsumer, PoseStack.Pose pPose, int r, int g, int b, boolean fadeOut) {
        float halfWidth = width / 2;
        int endAlpha = fadeOut ? 0 : 255;
        int light = LightCoordsUtil.pack(15, 15);
        beamVertex(pConsumer, pPose, -halfWidth, -halfWidth, 0, r, g, b, 255, 0, 0, light);
        beamVertex(pConsumer, pPose, -halfWidth, halfWidth, 0, r, g, b, 255, 0, 1, light);
        beamVertex(pConsumer, pPose, -halfWidth, halfWidth, z, r, g, b, endAlpha, 1, 1, light);
        beamVertex(pConsumer, pPose, -halfWidth, -halfWidth, z, r, g, b, endAlpha, 1, 0, light);

        beamVertex(pConsumer, pPose, -halfWidth, halfWidth, 0, r, g, b, 255, 0, 0, light);
        beamVertex(pConsumer, pPose, halfWidth, halfWidth, 0, r, g, b, 255, 0, 1, light);
        beamVertex(pConsumer, pPose, halfWidth, halfWidth, z, r, g, b, endAlpha, 1, 1, light);
        beamVertex(pConsumer, pPose, -halfWidth, halfWidth, z, r, g, b, endAlpha, 1, 0, light);

        beamVertex(pConsumer, pPose, halfWidth, halfWidth, 0, r, g, b, 255, 0, 0, light);
        beamVertex(pConsumer, pPose, halfWidth, -halfWidth, 0, r, g, b, 255, 0, 1, light);
        beamVertex(pConsumer, pPose, halfWidth, -halfWidth, z, r, g, b, endAlpha, 1, 1, light);
        beamVertex(pConsumer, pPose, halfWidth, halfWidth, z, r, g, b, endAlpha, 1, 0, light);

        beamVertex(pConsumer, pPose, halfWidth, -halfWidth, 0, r, g, b, 255, 0, 1, light);
        beamVertex(pConsumer, pPose, -halfWidth, -halfWidth, 0, r, g, b, 255, 0, 1, light);
        beamVertex(pConsumer, pPose, -halfWidth, -halfWidth, z, r, g, b, endAlpha, 1, 1, light);
        beamVertex(pConsumer, pPose, halfWidth, -halfWidth, z, r, g, b, endAlpha, 1, 0, light);
    }

    private static void beamVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                                   int r, int g, int b, int a, float u, float v, int light) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setLight(light)
                .setOverlay(0)
                .setNormal(pose, 0, 1, 0);
    }

    public static class LaserBeamRenderState {
        protected static final RenderType LASER_BEAM = RenderTypes.entityTranslucentEmissive(LASER_BEAM_TEXTURE);
		protected static final RenderType LASER_BEAM_ENTITY = RenderTypes.entityTranslucentEmissive(LASER_BEAM_TEXTURE);

        public static RenderType getLaserBeam() {
            return LASER_BEAM;
        }

		public static RenderType getLaserBeamEntity() {
			return LASER_BEAM_ENTITY;
		}
	}
}
