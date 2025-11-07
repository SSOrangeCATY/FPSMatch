package com.phasetranscrystal.fpsmatch.util;

import com.phasetranscrystal.fpsmatch.compat.LrtacticalCompat;
import com.phasetranscrystal.fpsmatch.impl.FPSMImpl;
import com.tacz.guns.api.item.IGun;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        String key = stack.getDescriptionId();
        if (key.startsWith("item.")) key = key.substring(5);
        String last = key.substring(key.lastIndexOf('.') + 1);
        String[] t = last.split("_");
        for (int i = 0; i < t.length; i++) {
            t[i] = Character.toUpperCase(t[i].charAt(0)) + t[i].substring(1).toLowerCase();
        }
        return String.join(" ", t);
    }

    public static String i18n(ItemStack st) {
        String unknown = I18n.exists("fpsm.unknown_weapon") ? I18n.get("fpsm.unknown_weapon") : "未知武器";
        if (st == null || st.isEmpty()) return unknown;
        IGun iGun = IGun.getIGunOrNull(st);
        if (iGun != null) {
            ResourceLocation gid = iGun.getGunId(st);
            String[] keys = new String[]{
                    "item." + gid.getNamespace() + "." + gid.getPath(),
                    "gun." + gid.getNamespace() + "." + gid.getPath(),
                    gid.getNamespace() + "." + gid.getPath(),
                    "tacz.gun." + gid.getPath(),
                    "tacz." + gid.getPath()
            };
            for (String k : keys) if (I18n.exists(k)) return I18n.get(k);
            return gid.getPath().toUpperCase(Locale.ROOT);
        }

        if(FPSMImpl.findEquipmentMod()){
            String i18n = LrtacticalCompat.i18n(st);
            if(i18n != null) return i18n;
        }

        String desc = st.getDescriptionId();
        if (I18n.exists(desc)) return I18n.get(desc);
        return st.getHoverName().getString();
    }
}
