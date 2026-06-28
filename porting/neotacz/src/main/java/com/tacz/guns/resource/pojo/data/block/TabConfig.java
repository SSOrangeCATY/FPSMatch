package com.tacz.guns.resource.pojo.data.block;

import com.google.gson.*;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.serialize.ItemStackJsonHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Supplier;

public record TabConfig(Identifier id, String name, Supplier<ItemStack> iconSupplier) {
    public static final Identifier TAB_AMMO = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ammo");

    public static final Identifier TAB_PISTOL = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pistol");
    public static final Identifier TAB_SNIPER = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "sniper");
    public static final Identifier TAB_RIFLE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rifle");
    public static final Identifier TAB_SHOTGUN = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "shotgun");
    public static final Identifier TAB_SMG = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "smg");
    public static final Identifier TAB_RPG = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rpg");
    public static final Identifier TAB_MG = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "mg");

    public static final Identifier TAB_SCOPE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope");
    public static final Identifier TAB_MUZZLE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "muzzle");
    public static final Identifier TAB_STOCK = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "stock");
    public static final Identifier TAB_GRIP = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "grip");
    public static final Identifier TAB_EXTENDED_MAG = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "extended_mag");
    public static final Identifier TAB_LASER = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "laser");

    public static final Identifier TAB_MISC = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "misc");
    public static final Identifier TAB_EMPTY = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "empty");

    public static final List<TabConfig> DEFAULT_TABS = List.of(
            new TabConfig(TabConfig.TAB_AMMO, "tacz.type.ammo.name", () -> AmmoItemBuilder.create().setId(DefaultAssets.DEFAULT_AMMO_ID).build()),
            new TabConfig(TabConfig.TAB_PISTOL, "tacz.type.pistol.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "glock_17")).forceBuild()),
            new TabConfig(TabConfig.TAB_SNIPER, "tacz.type.sniper.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ai_awp")).forceBuild()),
            new TabConfig(TabConfig.TAB_RIFLE, "tacz.type.rifle.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ak47")).forceBuild()),
            new TabConfig(TabConfig.TAB_SHOTGUN, "tacz.type.shotgun.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "db_short")).forceBuild()),
            new TabConfig(TabConfig.TAB_SMG, "tacz.type.smg.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "hk_mp5a5")).forceBuild()),
            new TabConfig(TabConfig.TAB_RPG, "tacz.type.rpg.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rpg7")).forceBuild()),
            new TabConfig(TabConfig.TAB_MG, "tacz.type.mg.name", () -> GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "m249")).forceBuild()),
            new TabConfig(TabConfig.TAB_SCOPE, "tacz.type.scope.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_acog_ta31")).build()),
            new TabConfig(TabConfig.TAB_MUZZLE, "tacz.type.muzzle.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "muzzle_compensator_trident")).build()),
            new TabConfig(TabConfig.TAB_STOCK, "tacz.type.stock.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "stock_militech_b5")).build()),
            new TabConfig(TabConfig.TAB_GRIP, "tacz.type.grip.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "grip_magpul_afg_2")).build()),
            new TabConfig(TabConfig.TAB_EXTENDED_MAG, "tacz.type.extended_mag.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "extended_mag_3")).build()),
            new TabConfig(TabConfig.TAB_LASER, "tacz.type.laser.name", () -> AttachmentItemBuilder.create().setId(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "laser_compact")).build()),
            new TabConfig(TabConfig.TAB_MISC, "tacz.type.misc.name", () -> ModItems.GUN_SMITH_TABLE.get().getDefaultInstance())
    );

    public TabConfig {
        if (iconSupplier == null) {
            iconSupplier = () -> ItemStack.EMPTY;
        }
    }

    public TabConfig(Identifier id, String name, ItemStack icon) {
        this(id, name, () -> icon == null ? ItemStack.EMPTY : icon.copy());
    }

    public ItemStack icon() {
        try {
            ItemStack icon = iconSupplier.get();
            return icon == null ? ItemStack.EMPTY : icon.copy();
        } catch (JsonParseException | IllegalArgumentException | NullPointerException exception) {
            GunMod.LOGGER.warn("Failed to build tab icon for {}", id, exception);
            return ItemStack.EMPTY;
        }
    }

    public static class Deserializer implements JsonDeserializer<TabConfig> {
        @Override
        public TabConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("TabConfig must be a JSON object");
            }
            JsonObject object = json.getAsJsonObject();
            if (!object.has("id") || !object.get("id").isJsonPrimitive()) {
                throw new JsonParseException("TabConfig must have an id");
            }
            Identifier id = context.deserialize(object.get("id"), Identifier.class);
            JsonObject icon = GsonHelper.getAsJsonObject(object, "icon").deepCopy();
            String name = GsonHelper.getAsString(object, "name", "tacz.type.unknown.name");
            // In 26.1, item data components are not bound while common block data is parsed.
            // Tab icons are GUI-only, so defer stack creation until the screen renders them.
            return new TabConfig(id, name, () -> ItemStackJsonHelper.getItemStack(icon, true));
        }
    }

    @NotNull
    public Component getName() {
        return Component.translatable(name==null ? "tacz.type.unknown.name" : name);
    }
}
