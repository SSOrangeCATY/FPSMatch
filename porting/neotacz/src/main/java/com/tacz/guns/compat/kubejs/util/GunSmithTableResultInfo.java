package com.tacz.guns.compat.kubejs.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.tacz.guns.api.item.nbt.ItemStackNbtHelper;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.resource.serialize.ItemStackJsonHelper;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.ItemWrapper;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.rhino.Context;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

public class GunSmithTableResultInfo {
    private static final String TYPE_KEY = "type";
    private static final String ID_KEY = "id";
    private static final String COUNT_KEY = "count";
    private static final String NBT_KEY = "nbt";
    private static final String CUSTOM_ITEM_KEY = "item";
    private static final String OUTPUT_GROUP_KEY = "group";
    private final JsonObject json;

    private GunSmithTableResultInfo() {
        this(new JsonObject());
    }

    private GunSmithTableResultInfo(JsonObject jsonObject) {
        this.json = (jsonObject != null) ? jsonObject : new JsonObject();
    }

    public static GunSmithTableResultInfo create() {
        return new GunSmithTableResultInfo();
    }

    public static GunSmithTableResultInfo createFromJson(JsonObject jsonObject) {
        return new GunSmithTableResultInfo(jsonObject);
    }

    public static GunSmithTableResultInfo createFromItemStack(ItemStack stack) {
        GunSmithTableResultInfo info = create().setType(GunSmithTableResult.CUSTOM);
        JsonObject itemJson = new JsonObject();
        itemJson.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        itemJson.addProperty("count", stack.getCount());
        CompoundTag tag = ItemStackNbtHelper.getTag(stack);
        if (!tag.isEmpty()) {
            itemJson.addProperty("nbt", tag.toString());
        }
        info.setCustomItem(itemJson);
        return info;
    }

    /**
     * {@link GunSmithTableResultInfo}的TypeWrapper, 将其他类型转为{@link GunSmithTableResultInfo}
     * object为其他类型时优先解析{@link JsonObject}，其次{@link ItemStack}
     * 之后尝试转化为{@link String}解析为{@link Identifier}
     * @param object 输入待转化对象
     * @return {@link GunSmithTableResultInfo}
     */
    public static GunSmithTableResultInfo of(Object object) {
        if (object instanceof GunSmithTableResultInfo info) {
            return info;
        } else if (object instanceof JsonObject jsonObject) {
            return createFromJson(jsonObject);
        } else if (object instanceof JsonElement jsonElement && jsonElement.isJsonObject()) {
            return createFromJson(jsonElement.getAsJsonObject());
        } else if (object instanceof ItemStack stack) {
            return createFromItemStack(stack);
        }
        String idString = object.toString();
        if (Identifier.tryParse(idString) != null) {
            Identifier rl = Identifier.parse(idString);
            TimelessItemWrapper.ItemIndexInfo indexInfo = TimelessItemWrapper.ItemIndexInfo.createFromResourceLocation(rl);
            if (indexInfo.isValidForRecipe()) {
                return create().setType(indexInfo.getParent()).setId(indexInfo.getIndexId());
            }
        }
        throw new IllegalArgumentException("Cannot convert " + object + " to GunSmithTableResultInfo without KubeJS script context");
    }

    public static GunSmithTableResultInfo of(Context cx, Object object) {
        if (object instanceof GunSmithTableResultInfo || object instanceof JsonObject || object instanceof JsonElement || object instanceof ItemStack) {
            return of(object);
        }
        String idString = object.toString();
        if (Identifier.tryParse(idString) != null) {
            return of(idString);
        }
        ItemStack stack = ItemWrapper.wrapResult(cx, object).result().orElse(ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            return createFromItemStack(stack);
        }
        return createFromJson(JsonUtils.objectOf(cx, object));
    }

    public String getType() {
        return GsonHelper.getAsString(this.json, TYPE_KEY);
    }

    public GunSmithTableResultInfo setType(String typeName) {
        this.json.addProperty(TYPE_KEY, typeName);
        return this;
    }

    public Identifier getId() {
        return Identifier.tryParse(GsonHelper.getAsString(this.json, ID_KEY));
    }

    public GunSmithTableResultInfo setId(Identifier id) {
        this.json.addProperty(ID_KEY, id.toString());
        return this;
    }

    public CompoundTag getNbt() {
        if (!this.json.has(NBT_KEY)) {
            return new CompoundTag();
        }
        return ItemStackJsonHelper.getNbt(this.json.get(NBT_KEY));
    }

    public GunSmithTableResultInfo setNbt(CompoundTag nbt) {
        this.json.addProperty(NBT_KEY, nbt.toString());
        return this;
    }

    public int getCount() {
        return this.json.has(COUNT_KEY) ? Math.max(GsonHelper.getAsInt(this.json, COUNT_KEY), 1) : 1;
    }

    public GunSmithTableResultInfo setCount(int count) {
        this.json.addProperty(COUNT_KEY, count);
        return this;
    }

    public JsonObject getCustomItem() {
        return this.json.has(CUSTOM_ITEM_KEY) ? GsonHelper.getAsJsonObject(this.json, CUSTOM_ITEM_KEY) : new JsonObject();
    }

    public GunSmithTableResultInfo setCustomItem(JsonObject itemJson) {
        this.json.add(CUSTOM_ITEM_KEY, itemJson);
        return this;
    }

    public GunSmithTableResultInfo setGroupName(String groupName) {
        this.json.addProperty(OUTPUT_GROUP_KEY, groupName);
        return this;
    }

    public GunSmithTableResultInfo setGroup(OutputGroupName group) {
        this.setGroupName(group.getName());
        return this;
    }

    public JsonObject toJson() {
        return json;
    }

    public enum OutputGroupName {
        AMMO("ammo"),
        EXTENDED_MAG("extended_mag"),
        GRIP("grip"),
        MG("mg"),
        MUZZLE("muzzle"),
        PISTOL("pistol"),
        RIFLE("rifle"),
        RPG("rpg"),
        SCOPE("scope"),
        SHOTGUN("shotgun"),
        SMG("smg"),
        SNIPER("sniper"),
        STOCK("stock");

        private final String name;

        OutputGroupName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
