package com.phasetranscrystal.fpsmatch.util;

import com.phasetranscrystal.fpsmatch.compat.gun.GunCompatManager;
import com.phasetranscrystal.fpsmatch.compat.gun.IGunProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public class FPSMFormatUtil {
    public static String fmt2(double v){ return String.format(Locale.US, "%.2f", v); }

    public static Component formatBoolean(boolean value) {
        return Component.literal(String.valueOf(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    /**
     * 将物品的描述ID转换为英文名称（首字母大写）
     *
     * @param stack 物品栈
     * @return 格式化后的英文名称
     */
    public static String en(ItemStack stack) {
        String key = stack.getItem().getDescriptionId();
        if (key.startsWith("item.")) key = key.substring(5);
        String last = key.substring(key.lastIndexOf('.') + 1);
        String[] t = last.split("_");
        for (int i = 0; i < t.length; i++) {
            t[i] = Character.toUpperCase(t[i].charAt(0)) + t[i].substring(1).toLowerCase();
        }
        return String.join(" ", t);
    }

    public static String i18n(ItemStack st) {
        String unknown = Component.translatable("fpsm.unknown_weapon").getString();
        if (st == null || st.isEmpty()) return unknown;
        IGunProvider provider = GunCompatManager.findProvider(st);
        if (provider.isGun(st)) {
            Identifier gid = provider.getGunId(st);
            String hoverName = st.getHoverName().getString();
            return hoverName.isBlank() ? gid.getPath().toUpperCase(Locale.ROOT) : hoverName;
        }

        return st.getHoverName().getString();
    }
}
