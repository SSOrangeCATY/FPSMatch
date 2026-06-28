package com.tacz.guns.client.tooltip;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.input.RefitKey;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.client.resource.pojo.display.gun.DamageStyle;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.item.GunTooltipPart;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

import static com.tacz.guns.item.ModernKineticGunItem.DefaultPropertyModification.SLUGS;

public final class GunTooltipTextBuilder {
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##%");
    private static final DecimalFormat FORMAT_P_D1 = new DecimalFormat("#.#%");
    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("#.##");

    private GunTooltipTextBuilder() {
    }

    public static void appendGunText(ItemStack gun, IGun iGun, CommonGunIndex gunIndex, Consumer<Component> lines) {
        GunDisplayInstance display = TimelessAPI.getGunDisplay(gun).orElse(null);
        BulletData bulletData = gunIndex.getBulletData();
        GunData gunData = gunIndex.getGunData();

        if (shouldShow(gun, GunTooltipPart.DESCRIPTION)) {
            @Nullable String tooltip = gunIndex.getPojo().getTooltip();
            if (tooltip != null) {
                lines.accept(Component.translatable(tooltip).withStyle(ChatFormatting.GRAY));
            }
        }

        if (shouldShow(gun, GunTooltipPart.BASE_INFO)) {
            appendBaseInfo(gun, iGun, gunIndex, gunData, bulletData, display, lines);
        }

        if (shouldShow(gun, GunTooltipPart.EXTRA_DAMAGE_INFO)) {
            appendExtraInfo(gun, gunData, bulletData, lines);
        }

        if (shouldShow(gun, GunTooltipPart.UPGRADES_TIP)) {
            String keyName = Component.keybind(RefitKey.REFIT_KEY.getName()).getString().toUpperCase(Locale.ENGLISH);
            lines.accept(Component.translatable("tooltip.tacz.gun.tips", keyName)
                    .withStyle(ChatFormatting.YELLOW)
                    .withStyle(ChatFormatting.ITALIC));
        }

        if (shouldShow(gun, GunTooltipPart.PACK_INFO)) {
            Identifier gunId = iGun.getGunId(gun);
            PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(gunId);
            if (packInfoObject != null) {
                lines.accept(Component.translatable(packInfoObject.getName())
                        .withStyle(ChatFormatting.BLUE)
                        .withStyle(ChatFormatting.ITALIC));
            }
        }
    }

    public static void appendBlockText(Identifier blockId, Consumer<Component> lines) {
        TimelessAPI.getClientBlockIndex(blockId).ifPresent(index -> {
            @Nullable String tooltipKey = index.getTooltipKey();
            if (tooltipKey != null) {
                Arrays.stream(I18n.get(tooltipKey).split("\n"))
                        .map(line -> Component.literal(line).withStyle(ChatFormatting.GRAY))
                        .forEach(lines);
            }
        });

        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(blockId);
        if (packInfoObject != null) {
            lines.accept(Component.translatable(packInfoObject.getName())
                    .withStyle(ChatFormatting.BLUE)
                    .withStyle(ChatFormatting.ITALIC));
        }
    }

    private static void appendBaseInfo(ItemStack gun, IGun iGun, CommonGunIndex gunIndex, GunData gunData,
                                       BulletData bulletData, @Nullable GunDisplayInstance display,
                                       Consumer<Component> lines) {
        int expToNextLevel = iGun.getExpToNextLevel(gun);
        int expCurrentLevel = iGun.getExpCurrentLevel(gun);
        int level = iGun.getLevel(gun);
        if (level >= iGun.getMaxLevel()) {
            lines.accept(Component.translatable("tooltip.tacz.gun.level")
                    .append(Component.literal("%d (MAX)".formatted(level)).withStyle(ChatFormatting.DARK_PURPLE)));
        } else {
            float progress = expCurrentLevel / (float) (expToNextLevel + expCurrentLevel) * 100.0F;
            lines.accept(Component.translatable("tooltip.tacz.gun.level")
                    .append(Component.literal("%d (%.1f%%)".formatted(level, progress)).withStyle(ChatFormatting.YELLOW)));
        }

        String tabKey = "tacz.type." + gunIndex.getType() + ".name";
        lines.accept(Component.translatable("tooltip.tacz.gun.type")
                .append(Component.translatable(tabKey).withStyle(ChatFormatting.AQUA)));

        double damage = AttachmentDataUtils.getDamageWithAttachment(gun, gunData);
        boolean hasSlugInstalled = AllowAttachmentTagMatcher.matchTag(SLUGS, iGun.getAttachmentId(gun, AttachmentType.EXTENDED_MAG));
        int bulletAmount = hasSlugInstalled ? 1 : gunData.getBulletData().getBulletAmount();
        MutableComponent value;
        if (display != null && display.getDamageStyle() == DamageStyle.PER_PROJECTILE && bulletAmount > 1) {
            value = Component.literal(DAMAGE_FORMAT.format(damage / bulletAmount) + "x" + bulletAmount).withStyle(ChatFormatting.AQUA);
        } else {
            value = Component.literal(DAMAGE_FORMAT.format(damage)).withStyle(ChatFormatting.AQUA);
        }
        if (bulletData.getExplosionData() != null && (AttachmentDataUtils.isExplodeEnabled(gun, gunData) || bulletData.getExplosionData().isExplode())) {
            value.append(" + ")
                    .append(DAMAGE_FORMAT.format(bulletData.getExplosionData().getDamage() * SyncConfig.DAMAGE_BASE_MULTIPLIER.get()))
                    .append(Component.translatable("tooltip.tacz.gun.explosion"));
        }
        lines.accept(Component.translatable("tooltip.tacz.gun.damage").append(value));
    }

    private static void appendExtraInfo(ItemStack gun, GunData gunData, BulletData bulletData, Consumer<Component> lines) {
        @Nullable ExtraDamage extraDamage = bulletData.getExtraDamage();
        double armorDamagePercent = extraDamage == null ? 0.0D : AttachmentDataUtils.getArmorIgnoreWithAttachment(gun, gunData);
        double headShotMultiplierPercent = extraDamage == null ? 1.0D : AttachmentDataUtils.getHeadshotMultiplier(gun, gunData);
        armorDamagePercent = Mth.clamp(armorDamagePercent, 0.0F, 1.0F);

        lines.accept(Component.translatable("tooltip.tacz.gun.armor_ignore", FORMAT.format(armorDamagePercent))
                .withStyle(ChatFormatting.GOLD));
        lines.accept(Component.translatable("tooltip.tacz.gun.head_shot_multiplier", FORMAT.format(headShotMultiplierPercent))
                .withStyle(ChatFormatting.GOLD));

        double weightFactor = SyncConfig.WEIGHT_SPEED_MULTIPLIER.get();
        double weight = AttachmentDataUtils.getWightWithAttachment(gun, gunData);
        lines.accept(Component.translatable("tooltip.tacz.gun.movement_speed", FORMAT_P_D1.format(-weightFactor * weight))
                .withStyle(ChatFormatting.RED));
    }

    private static boolean shouldShow(ItemStack gun, GunTooltipPart part) {
        return (GunTooltipPart.getHideFlags(gun) & part.getMask()) == 0;
    }
}
