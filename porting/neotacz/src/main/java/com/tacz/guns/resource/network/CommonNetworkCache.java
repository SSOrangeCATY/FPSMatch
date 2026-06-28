package com.tacz.guns.resource.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.ICommonResourceProvider;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.block.BlockData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;

import java.util.*;

/**
 * 网络位置的缓存<br/>
 * 用于存储从网络获取的数据
 */
public enum CommonNetworkCache implements ICommonResourceProvider {
    INSTANCE;

    public Map<Identifier, GunData> gunData = new HashMap<>();
    public Map<Identifier, AttachmentData> attachmentData = new HashMap<>();
    public Map<Identifier, RecipeFilter> recipeFilter = new HashMap<>();
    public Map<Identifier, BlockData> blockData = new HashMap<>();
    public Map<Identifier, CommonGunIndex> gunIndex = new HashMap<>();
    public Map<Identifier, CommonAmmoIndex> ammoIndex = new HashMap<>();
    public Map<Identifier, CommonAttachmentIndex> attachmentIndex = new HashMap<>();
    public Map<Identifier, CommonBlockIndex> blockIndex = new HashMap<>();
    public Map<Identifier, GunSmithTableRecipe> recipes = new HashMap<>();
    public Map<Identifier, Set<String>> attachmentTags = new HashMap<>();
    public Map<Identifier, Set<String>> allowAttachmentTags = new HashMap<>();

    @Nullable
    @Override
    public GunData getGunData(Identifier id) {
        return gunData.get(id);
    }

    @Nullable
    @Override
    public AttachmentData getAttachmentData(Identifier attachmentId) {
        return attachmentData.get(attachmentId);
    }

    @Nullable
    @Override
    public BlockData getBlockData(Identifier id) {
        return blockData.get(id);
    }

    @Nullable
    @Override
    public RecipeFilter getRecipeFilter(Identifier id) {
        return recipeFilter.get(id);
    }

    @Nullable
    @Override
    public CommonGunIndex getGunIndex(Identifier id) {
        return gunIndex.get(id);
    }

    @Override
    public @Nullable CommonAmmoIndex getAmmoIndex(Identifier ammoId) {
        return ammoIndex.get(ammoId);
    }

    @Override
    public @Nullable CommonAttachmentIndex getAttachmentIndex(Identifier attachmentId) {
        return attachmentIndex.get(attachmentId);
    }

    @Override
    public @Nullable CommonBlockIndex getBlockIndex(Identifier blockId) {
        return blockIndex.get(blockId);
    }

    @Override
    public @Nullable LuaTable getScript(Identifier scriptId) {
        return null; // 脚本不需要同步
    }

    @Override
    public Set<Map.Entry<Identifier, CommonGunIndex>> getAllGuns() {
        return gunIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<Identifier, CommonAmmoIndex>> getAllAmmos() {
        return ammoIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<Identifier, CommonAttachmentIndex>> getAllAttachments() {
        return attachmentIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<Identifier, CommonBlockIndex>> getAllBlocks() {
        return blockIndex.entrySet();
    }

    @Nullable
    public GunSmithTableRecipe getRecipe(Identifier id) {
        return recipes.get(id);
    }

    public Set<Map.Entry<Identifier, GunSmithTableRecipe>> getAllRecipes() {
        return recipes.entrySet();
    }

    @Override
    public Set<String> getAttachmentTags(Identifier registryName) {
        return attachmentTags.get(registryName);
    }

    @Override
    public Set<String> getAllowAttachmentTags(Identifier registryName) {
        return allowAttachmentTags.get(registryName);
    }

    public void clear() {
        gunData.clear();
        attachmentData.clear();
        gunIndex.clear();
        ammoIndex.clear();
        attachmentIndex.clear();
        blockIndex.clear();
        recipes.clear();
        recipeFilter.clear();
        blockData.clear();

        attachmentTags.clear();
        allowAttachmentTags.clear();
        AllowAttachmentTagMatcher.resetCache();
    }

    public void fromNetwork(Map<DataType, Map<Identifier, String>> cache) {
        clear();
        EnumSet<DataType> handled = EnumSet.noneOf(DataType.class);

        readNetworkType(cache, handled, DataType.GUN_DATA);
        readNetworkType(cache, handled, DataType.ATTACHMENT_DATA);
        readNetworkType(cache, handled, DataType.BLOCK_DATA);
        readNetworkType(cache, handled, DataType.RECIPE_FILTER);
        readNetworkType(cache, handled, DataType.ATTACHMENT_TAGS);
        readNetworkType(cache, handled, DataType.ALLOW_ATTACHMENT_TAGS);

        readNetworkType(cache, handled, DataType.AMMO_INDEX);
        readNetworkType(cache, handled, DataType.GUN_INDEX);
        readNetworkType(cache, handled, DataType.ATTACHMENT_INDEX);
        readNetworkType(cache, handled, DataType.BLOCK_INDEX);

        // Recipe init resolves result groups through the indexes above; parsing recipes earlier permanently collapses some groups to tacz:empty.
        readNetworkType(cache, handled, DataType.RECIPES);

        for (Map.Entry<DataType, Map<Identifier, String>> entry : cache.entrySet()) {
            if (handled.contains(entry.getKey())) {
                continue;
            }
            fromNetwork(entry.getKey(), entry.getValue());
        }
    }

    private void readNetworkType(Map<DataType, Map<Identifier, String>> cache, EnumSet<DataType> handled, DataType type) {
        Map<Identifier, String> data = cache.get(type);
        if (data != null) {
            fromNetwork(type, data);
            handled.add(type);
        }
    }

    private <T> T parse(String json, Class<T> dataClass) {
        return CommonAssetsManager.GSON.fromJson(json, dataClass);
    }

    private AttachmentData parseAttachmentData(String json) {
        AttachmentData data = CommonAssetsManager.GSON.fromJson(json, AttachmentData.class);
        JsonElement element = CommonAssetsManager.GSON.fromJson(json, JsonElement.class);
        if (data != null) {
            // 序列化注册的配件属性修改
            AttachmentPropertyManager.getModifiers().forEach((key, value) -> {
                if (!element.isJsonObject()) {
                    return;
                }
                JsonObject jsonObject = element.getAsJsonObject();
                if (jsonObject.has(key)) {
                    JsonProperty<?> property = value.readJson(json);
                    property.initComponents();
                    data.addModifier(key, property);
                } else if (jsonObject.has(value.getOptionalFields())) {
                    // 为了兼容旧版本，读取可选字段名
                    JsonProperty<?> property = value.readJson(json);
                    property.initComponents();
                    data.addModifier(key, property);
                }
            });
        }
        return data;
    }

    private void resolveAttachmentTags(Map<Identifier, String> data) {
        for (Map.Entry<Identifier, String> entry : data.entrySet()) {
            List<String> tags = CommonAssetsManager.GSON.fromJson(entry.getValue(), new TypeToken<>(){});
            if (entry.getKey().getPath().startsWith("allow_attachments/") && entry.getKey().getPath().length()>18) {
                Identifier gunId = entry.getKey().withPath(entry.getKey().getPath().substring(18));
                allowAttachmentTags.computeIfAbsent(gunId, (v) -> new HashSet<>()).addAll(tags);
            } else {
                attachmentTags.computeIfAbsent(entry.getKey(), (v) -> new HashSet<>()).addAll(tags);
            }
        }
    }


    private void fromNetwork(DataType type, Map<Identifier, String> data) {
        for (Map.Entry<Identifier, String> entry : data.entrySet()) {
            try {
                switch (type) {
                    case GUN_DATA -> gunData.put(entry.getKey(), parse(entry.getValue(), GunData.class));
                    case GUN_INDEX -> gunIndex.put(entry.getKey(), parse(entry.getValue(), CommonGunIndex.class));
                    case AMMO_INDEX -> ammoIndex.put(entry.getKey(), parse(entry.getValue(), CommonAmmoIndex.class));
                    case ATTACHMENT_DATA -> attachmentData.put(entry.getKey(), parseAttachmentData(entry.getValue()));
                    case ATTACHMENT_INDEX -> attachmentIndex.put(entry.getKey(), parse(entry.getValue(), CommonAttachmentIndex.class));
                    case ATTACHMENT_TAGS -> resolveAttachmentTags(data);
                    case ALLOW_ATTACHMENT_TAGS -> resolveAttachmentTags(data);
                    case RECIPES -> recipes.put(entry.getKey(), parseRecipe(entry.getKey(), entry.getValue()));
                    case BLOCK_INDEX -> blockIndex.put(entry.getKey(), parse(entry.getValue(), CommonBlockIndex.class));
                    case RECIPE_FILTER -> recipeFilter.put(entry.getKey(), parse(entry.getValue(), RecipeFilter.class));
                    case BLOCK_DATA -> blockData.put(entry.getKey(), parse(entry.getValue(), BlockData.class));
                }
            } catch (IllegalArgumentException | JsonParseException exception) {
                GunMod.LOGGER.warn("Failed to parse data from network for {} with id {}", type, entry.getKey(), exception);
            }
        }
    }

    private GunSmithTableRecipe parseRecipe(Identifier id, String json) {
        TableRecipe tableRecipe = CommonAssetsManager.GSON.fromJson(json, TableRecipe.class);
        return new GunSmithTableRecipe(id, tableRecipe);
    }
}
