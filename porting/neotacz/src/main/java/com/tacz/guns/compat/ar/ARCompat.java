package com.tacz.guns.compat.ar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class ARCompat {

	public static final String MOD_ID = "acceleratedrendering";

	public static boolean LOADED;

	public static void init() {
		LOADED = false;
	}

	public static boolean shouldAccelerate() {
		return false;
	}

	public static boolean isAccelerated(VertexConsumer vertexConsumer) {
		return false;
	}

	public static void setRenderingLevel() {
	}

	public static void resetRenderingLevel() {
	}

	public static void setRenderLayer(int layer) {
	}

	public static void setRenderBeforeFunction(Runnable runnable) {
	}

	public static void setRenderAfterFunction(Runnable runnable) {
	}

	public static void resetRenderLayer() {
	}

	public static void resetRenderBeforeFunction() {
	}

	public static void resetRenderAfterFunction() {
	}

	public static void disableAcceleration() {
	}

	public static void resetAcceleration() {
	}

	// 防止类意外加载 (直接在BeamRenderer类使用AcceleratedBeamRenderer.INSTANCE在会触发类加载)
	public static void renderLaser(
			VertexConsumer extension,
			float z,
			float width,
			boolean fadeOut,
			PoseStack poseStack,
			int color
	) {
	}
}
