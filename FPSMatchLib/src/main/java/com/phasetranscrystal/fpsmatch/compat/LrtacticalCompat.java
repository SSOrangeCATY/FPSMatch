package com.phasetranscrystal.fpsmatch.compat;

import me.xjqsh.lrtactical.api.LrTacticalAPI;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.item.IThrowable;
import me.xjqsh.lrtactical.client.resource.display.MeleeDisplayInstance;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

public class LrtacticalCompat {
    public static boolean isKnife(ItemStack stack) {
        return stack.getItem() instanceof IMeleeWeapon;
    }

    @OnlyIn(Dist.CLIENT)
    public static ResourceLocation getTexture(ItemStack stack) {
        Optional<MeleeDisplayInstance> optional = LrTacticalAPI.getMeleeDisplay(stack);
        if (optional.isPresent()) {
            MeleeDisplayInstance display = optional.get();
            return display.getTexture();
        }
        return null;
    }

    public static boolean itemCheck(Player player) {
        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        return check(main) || check(off);
    }

    public static boolean check(Item item) {
        return isKnife(item) || isThrowable(item);
    }

    public static boolean isKnife(Item item) {
        return item instanceof IMeleeWeapon;
    }

    public static boolean isThrowable(Item item) {
        return item instanceof IThrowable;
    }

    @Nullable
    public static String i18n(ItemStack itemStack) {
        IMeleeWeapon melee = IMeleeWeapon.of(itemStack);
        if (melee != null) {
            ResourceLocation id = melee.getDisplayId(itemStack);
            if (id == null || "lrtactical:empty".equals(id.toString())) id = melee.getId(itemStack);
            if (id != null) {
                String[] keys = new String[]{
                    "item." + id.getNamespace() + "" + id.getPath(),
                    "melee." + id.getNamespace() + "" + id.getPath(),
                    id.getNamespace() + "" + id.getPath(),
                        "lrtactical." + id.getPath(),
                        "lrtactical.melee." + id.getPath()
                };
                for (String k : keys) if (I18n.exists(k)) return I18n.get(k);
                return id.getPath().toUpperCase(Locale.ROOT);
            }
        }

        IThrowable thr = IThrowable.of(itemStack);
        if (thr != null) {
            ResourceLocation id = thr.getDisplayId(itemStack);
            if (id == null || "lrtactical:empty".equals(id.toString())) id = thr.getId(itemStack);
            if (id != null) {
                String[] keys = new String[]{
                    "item." + id.getNamespace() + "" + id.getPath(),
                    "throwable." + id.getNamespace() + "" + id.getPath(),
                    id.getNamespace() + "" + id.getPath(),
                        "lrtactical." + id.getPath(),
                        "lrtactical.throwable." + id.getPath()
                };
                for (String k : keys) if (I18n.exists(k)) return I18n.get(k);
                return id.getPath().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }
}
