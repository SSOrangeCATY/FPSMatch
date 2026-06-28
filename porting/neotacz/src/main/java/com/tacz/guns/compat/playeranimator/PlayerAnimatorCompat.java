package com.tacz.guns.compat.playeranimator;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.LivingEntity;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.zip.ZipFile;

public class PlayerAnimatorCompat {
    public static Identifier LOWER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "lower_animation");
    public static Identifier LOOP_UPPER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "loop_upper_animation");
    public static Identifier ONCE_UPPER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "once_upper_animation");
    public static Identifier ROTATION_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rotation");

    private static final String MOD_ID = "playeranimator";
    private static boolean INSTALLED = false;

    public static void init() {
        INSTALLED = false;
    }

    public static boolean loadAnimationFromZip(ZipFile zipFile, String zipPath) {
        return false;
    }

    public static void loadAnimationFromFile(File file) {
    }

    public static void clearAllAnimationCache() {
    }

    public static boolean hasPlayerAnimator3rd(LivingEntity livingEntity, GunDisplayInstance display) {
        return false;
    }

    public static void stopAllAnimation(LivingEntity livingEntity) {
    }

    public static void stopAllAnimation(LivingEntity livingEntity, int fadeTime) {
    }

    public static void playAnimation(LivingEntity livingEntity, GunDisplayInstance display, float limbSwingAmount) {
    }

    public static boolean isInstalled() {
        return INSTALLED;
    }

    public static void registerReloadListener(BiConsumer<Identifier, PreparableReloadListener> register) {
    }
}
