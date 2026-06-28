package com.tacz.guns.client.renderer.other;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.pojo.display.gun.LayerGunShow;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class HumanoidOffhandRender {
    public static void renderGun(LivingEntity entity, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight) {
        renderOffhandGun(entity, poseStack, submitNodeCollector, packedLight);
        renderHotbarGun(entity, poseStack, submitNodeCollector, packedLight);
    }

    private static void renderOffhandGun(LivingEntity entity, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight) {
        ItemStack itemStack = entity.getOffhandItem();
        if (itemStack.isEmpty()) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(itemStack);
        if (iGun == null) {
            return;
        }
        TimelessAPI.getGunDisplay(itemStack).ifPresent(display ->
                renderGunItem(entity, poseStack, submitNodeCollector, packedLight, itemStack, display.getOffhandShow()));
    }

    private static void renderHotbarGun(LivingEntity entity, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight) {
        if (!(entity instanceof Player player)) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (i == inventory.getSelectedSlot()) {
                continue;
            }
            renderHotbarGun(entity, poseStack, submitNodeCollector, packedLight, inventory.getItem(i), i);
        }
    }

    private static void renderHotbarGun(LivingEntity entity, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight,
                                        ItemStack itemStack, int inventoryIndex) {
        if (itemStack.isEmpty()) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(itemStack);
        if (iGun == null) {
            return;
        }
        TimelessAPI.getGunDisplay(itemStack).ifPresent(display -> {
            var hotbarShow = display.getHotbarShow();
            if (hotbarShow == null || hotbarShow.isEmpty() || !hotbarShow.containsKey(inventoryIndex)) {
                return;
            }
            renderGunItem(entity, poseStack, submitNodeCollector, packedLight, itemStack, hotbarShow.get(inventoryIndex));
        });
    }

    private static void renderGunItem(LivingEntity entity, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight,
                                      ItemStack itemStack, LayerGunShow gunShow) {
        if (gunShow == null) {
            return;
        }
        Vector3f pos = gunShow.getPos();
        Vector3f rotate = gunShow.getRotate();
        Vector3f scale = gunShow.getScale();

        poseStack.pushPose();
        poseStack.translate(-pos.x() / 16f, 1.5 - pos.y() / 16f, pos.z() / 16f);
        poseStack.scale(-scale.x(), -scale.y(), scale.z());
        Quaternionf rotation = new Quaternionf();
        MathUtil.toQuaternion((float) Math.toRadians(rotate.x), (float) Math.toRadians(rotate.y), (float) Math.toRadians(rotate.z), rotation);
        poseStack.mulPose(rotation);
        ItemStackRenderState itemState = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                itemState,
                itemStack,
                ItemDisplayContext.FIXED,
                entity.level(),
                entity,
                entity.getId() + ItemDisplayContext.FIXED.ordinal()
        );
        itemState.submit(poseStack, submitNodeCollector, packedLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
