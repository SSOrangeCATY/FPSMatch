package com.tacz.guns.resource;

import net.neoforged.fml.common.EventBusSubscriber;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.vmlib.LuaGunLogicConstant;
import com.tacz.guns.api.vmlib.LuaLibrary;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.manager.*;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.block.BlockData;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.Ignite;
import com.tacz.guns.resource.pojo.data.loot.LootTableInjection;
import com.tacz.guns.resource.serialize.*;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import com.tacz.guns.util.ItemStackData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;

import java.util.*;
import java.util.function.BiConsumer;

@EventBusSubscriber
public class CommonAssetsManager implements ICommonResourceProvider {
    private static CommonAssetsManager INSTANCE;
    private static final RegistryAccess.Frozen BUILTIN_REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Identifier.class, new IdentifierSerializer())
            .registerTypeAdapter(Pair.class, new PairSerializer())
            .registerTypeAdapter(GunSmithTableIngredient.class, new GunSmithTableIngredientSerializer())
            .registerTypeAdapter(GunSmithTableResult.class, new GunSmithTableResultSerializer())
            .registerTypeAdapter(ExtraDamage.DistanceDamagePair.class, new DistanceDamagePairSerializer())
            .registerTypeAdapter(Vec3.class, new Vec3Serializer())
            .registerTypeAdapter(Ignite.class, new IgniteSerializer())
            .registerTypeAdapter(RecipeFilter.class, new RecipeFilter.Deserializer())
            .registerTypeAdapter(CommonGunIndex.class, new CommonGunIndexSerializer())
            .registerTypeAdapter(CommonAmmoIndex.class, new CommonAmmoIndexSerializer())
            .registerTypeAdapter(CommonAttachmentIndex.class, new CommonAttachmentIndexSerializer())
            .registerTypeAdapter(CommonBlockIndex.class, new CommonBlockIndexSerializer())
            .registerTypeAdapter(TabConfig.class, new TabConfig.Deserializer())
            .create();

    private final List<INetworkCacheReloadListener> listeners = new ArrayList<>();
    private CommonDataManager<GunData> gunData;
    private CommonDataManager<AttachmentData> attachmentData;
    private CommonDataManager<BlockData> blockData;
    private CommonDataManager<CommonAmmoIndex> ammoIndex;
    private CommonDataManager<CommonGunIndex> gunIndex;
    private CommonDataManager<CommonAttachmentIndex> attachmentIndex;
    private CommonDataManager<CommonBlockIndex> blockIndex;
    private GunSmithTableRecipeDataManager recipeDataManager;
    private RecipeFilterManager recipeFilterManager;
    private LootInjectionManager lootInjectionManager;

    private AttachmentsTagManager attachmentsTagManager;
    List<LuaLibrary> libList = List.of(new LuaGunLogicConstant());
    private final ScriptManager scriptManager = new ScriptManager(new FileToIdConverter("scripts", ".lua"), libList);

    public void reloadAndRegister(BiConsumer<Identifier, PreparableReloadListener> register) {
        // 这里会顺序重载，所以需要把index这种依赖data的放在后面
        gunData = register(new CommonDataManager<>(DataType.GUN_DATA, GunData.class, GSON, "data/guns", "GunDataLoader"));
        attachmentData = register(new AttachmentDataManager());
        attachmentsTagManager = register(new AttachmentsTagManager());
        recipeFilterManager = register(new RecipeFilterManager());
        lootInjectionManager = new LootInjectionManager();
        register.accept(reloadId("common/loot_injection"), lootInjectionManager);
        blockData = register(new CommonDataManager<>(DataType.BLOCK_DATA, BlockData.class, GSON, "data/blocks", "BlockDataLoader"));
        register.accept(reloadId("common/scripts"), scriptManager);

        ammoIndex = register(new CommonDataManager<>(DataType.AMMO_INDEX, CommonAmmoIndex.class, GSON, "index/ammo", "AmmoIndexLoader"));
        gunIndex = register(new CommonDataManager<>(DataType.GUN_INDEX, CommonGunIndex.class, GSON, "index/guns", "GunIndexLoader"));
        attachmentIndex = register(new CommonDataManager<>(DataType.ATTACHMENT_INDEX, CommonAttachmentIndex.class, GSON, "index/attachments", "AttachmentIndexLoader"));
        blockIndex = register(new CommonDataManager<>(DataType.BLOCK_INDEX, CommonBlockIndex.class, GSON, "index/blocks", "BlockIndexLoader"));
        recipeDataManager = register(new GunSmithTableRecipeDataManager());

        listeners.forEach(listener -> register.accept(reloadId("common/" + listener.getType().name().toLowerCase(Locale.ROOT)), listener));
        register.accept(reloadId("common/allow_attachment_tag_reset"), new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void preparations, ResourceManager manager, ProfilerFiller profiler) {
                AllowAttachmentTagMatcher.resetCache();
            }
        });
    }

    private static Identifier reloadId(String path) {
        return Identifier.fromNamespaceAndPath(GunMod.MOD_ID, path);
    }

    private <T extends INetworkCacheReloadListener> T register(T listener) {
        listeners.add(listener);
        return listener;
    }

    public Map<DataType, Map<Identifier, String>> getNetworkCache() {
        ImmutableMap.Builder<DataType, Map<Identifier, String>> builder = ImmutableMap.builder();
        Set<DataType> addedTypes = EnumSet.noneOf(DataType.class);
        for (INetworkCacheReloadListener listener : listeners) {
            Map<Identifier, String> networkCache = listener.getNetworkCache();
            if (listener.getType() == DataType.RECIPES) {
                networkCache = selectRecipeNetworkCache(networkCache);
            }
            if (networkCache != null) {
                builder.put(listener.getType(), networkCache);
                addedTypes.add(listener.getType());
            }
        }
        Map<Identifier, String> recipes = addedTypes.contains(DataType.RECIPES) ? Map.of() : getRecipeNetworkCache();
        if (!recipes.isEmpty() && !addedTypes.contains(DataType.RECIPES)) {
            builder.put(DataType.RECIPES, recipes);
        }
        return builder.build();
    }

    private Map<Identifier, String> selectRecipeNetworkCache(@Nullable Map<Identifier, String> managerCache) {
        Map<Identifier, String> fallbackCache = getRecipeNetworkCache();
        if (managerCache == null || managerCache.isEmpty()) {
            return fallbackCache;
        }
        if (fallbackCache.size() > managerCache.size()) {
            GunMod.LOGGER.warn("Using RecipeManager gun smith table recipe fallback because it has more recipes: manager={} fallback={}",
                    managerCache.size(), fallbackCache.size());
            return fallbackCache;
        }
        return managerCache;
    }

    private Map<Identifier, String> getRecipeNetworkCache() {
        if (recipeManager == null) {
            return Map.of();
        }
        Map<Identifier, String> recipes = new LinkedHashMap<>();
        recipeManager.getRecipes().stream()
                .filter(holder -> holder.value().getType() == ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())
                .forEach(holder -> {
                    Identifier id = holder.id().identifier();
                    try {
                        GunSmithTableRecipe recipe = (GunSmithTableRecipe) holder.value();
                        recipe.init();
                        recipes.put(id, GSON.toJson(toNetworkRecipeJson(recipe)));
                    } catch (RuntimeException exception) {
                        GunMod.LOGGER.warn("Failed to serialize gun smith table recipe {} for client sync", id, exception);
                    }
                });
        return recipes;
    }

    private static JsonObject toNetworkRecipeJson(GunSmithTableRecipe recipe) {
        JsonObject root = new JsonObject();
        JsonArray materials = new JsonArray();
        for (GunSmithTableIngredient ingredient : recipe.getInputs()) {
            JsonObject material = new JsonObject();
            JsonElement item = Ingredient.CODEC.encodeStart(
                    BUILTIN_REGISTRIES.createSerializationContext(JsonOps.INSTANCE),
                    ingredient.getIngredient()
            ).getOrThrow(IllegalArgumentException::new);
            material.add("item", item);
            material.addProperty("count", ingredient.getCount());
            materials.add(material);
        }
        root.add("materials", materials);
        root.add("result", toNetworkResultJson(recipe.getResult()));
        return root;
    }

    private static JsonObject toNetworkResultJson(GunSmithTableResult result) {
        JsonObject resultJson = new JsonObject();
        resultJson.addProperty("type", GunSmithTableResult.CUSTOM);
        resultJson.add("item", toNetworkItemStackJson(result.getResult()));
        resultJson.addProperty("group", result.getGroup().toString());
        return resultJson;
    }

    private static JsonObject toNetworkItemStackJson(ItemStack stack) {
        JsonObject itemJson = new JsonObject();
        itemJson.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (stack.getCount() != 1) {
            itemJson.addProperty("count", stack.getCount());
        }
        CompoundTag customData = ItemStackData.copyCustomData(stack);
        if (!customData.isEmpty()) {
            itemJson.addProperty("nbt", customData.toString());
        }
        return itemJson;
    }

    @Nullable
    @Override
    public GunData getGunData(Identifier id) {
        return gunData.getData(id);
    }

    @Nullable
    @Override
    public AttachmentData getAttachmentData(Identifier id) {
        return attachmentData.getData(id);
    }

    @Nullable
    @Override
    public BlockData getBlockData(Identifier id) {
        return blockData.getData(id);
    }

    @Override
    @Nullable
    public RecipeFilter getRecipeFilter(Identifier id) {
        return recipeFilterManager.getFilter(id);
    }

    public List<LootTableInjection> getLootTableInjections(Identifier lootTable) {
        if (lootInjectionManager == null) {
            return List.of();
        }
        return lootInjectionManager.getInjections(lootTable);
    }

    @Nullable
    @Override
    public CommonGunIndex getGunIndex(Identifier gunId) {
        return gunIndex.getData(gunId);
    }

    @Override
    public Set<Map.Entry<Identifier, CommonGunIndex>> getAllGuns() {
        return gunIndex.getAllData().entrySet();
    }

    @Nullable
    @Override
    public CommonAmmoIndex getAmmoIndex(Identifier ammoId) {
        return ammoIndex.getData(ammoId);
    }

    @Override
    public Set<Map.Entry<Identifier, CommonAmmoIndex>> getAllAmmos() {
        return ammoIndex.getAllData().entrySet();
    }

    @Nullable
    @Override
    public CommonAttachmentIndex getAttachmentIndex(Identifier attachmentId) {
        return attachmentIndex.getData(attachmentId);
    }

    @Override
    public Set<Map.Entry<Identifier, CommonAttachmentIndex>> getAllAttachments() {
        return attachmentIndex.getAllData().entrySet();
    }

    @Override
    public LuaTable getScript(Identifier scriptId) {
        return scriptManager.getScript(scriptId);
    }

    @Nullable
    @Override
    public CommonBlockIndex getBlockIndex(Identifier blockId) {
        return blockIndex.getData(blockId);
    }

    @Override
    public Set<Map.Entry<Identifier, CommonBlockIndex>> getAllBlocks() {
        return blockIndex.getAllData().entrySet();
    }

    @Override
    public Set<String> getAttachmentTags(Identifier registryName) {
        return attachmentsTagManager.getAttachmentTags(registryName);
    }

    @Override
    public Set<String> getAllowAttachmentTags(Identifier registryName) {
        return attachmentsTagManager.getAllowAttachmentTags(registryName);
    }

    /**
     * 获取实例<br/>
     * 实例仅当内置服务器/专用服务器启动时才会被创建<br/>
     * 当客户端正连接到多人游戏时，该方法将返回 null
     * @return CommonAssetsManger实例
     */
    @Nullable
    public static CommonAssetsManager getInstance() {
        return INSTANCE;
    }

    public static void clearInstance() {
        INSTANCE = null;
    }

    /**
     * 根据当前环境选择合适的缓存<br/>
     * 当前环境为单人游戏或多人游戏的服务端时，返回CommonAssetsManger实例<br/>
     * 当前环境为多人游戏的客户端时，返回CommonNetworkCache实例
     * @return ICommonResourceProvider实例
     */
    public static ICommonResourceProvider get() {
        return INSTANCE == null ? CommonNetworkCache.INSTANCE : INSTANCE;
    }

    @SubscribeEvent
    public static void onReload(AddServerReloadListenersEvent event) {
        var commonAssetsManager = new CommonAssetsManager();
        commonAssetsManager.reloadAndRegister(event::addListener);
        INSTANCE = commonAssetsManager;
        INSTANCE.recipeManager = event.getServerResources().getRecipeManager();
    }

    public RecipeManager recipeManager;

    /**
     * 这个事件理论上会在server resource已经完成重载和传输到客户端之前触发<br/>
     * 尝试根据common data初始化延迟加载的配方
     * @param event
     */
    @SubscribeEvent
    public static void onReload(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD){
            if (getInstance() !=null && getInstance().recipeManager != null) {
                List<GunSmithTableRecipe> recipes = getInstance().recipeManager.getRecipes().stream()
                        .map(RecipeHolder::value)
                        .filter(recipe -> recipe.getType() == ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())
                        .map(recipe -> (GunSmithTableRecipe) recipe)
                        .toList();
                for (GunSmithTableRecipe recipe : recipes) {
                    recipe.init();
                }
            }
        }
    }


    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clearInstance();
    }

    @SubscribeEvent
    public static void OnDatapackSync(OnDatapackSyncEvent event) {
        if (getInstance() == null) {
            return;
        }
        Map<DataType, Map<Identifier, String>> networkCache = getInstance().getNetworkCache();
        logDatapackSyncCache(event, networkCache);
        ServerMessageSyncGunPack message = new ServerMessageSyncGunPack(networkCache);
        if (event.getPlayer() != null) {
            NetworkHandler.sendToClientPlayer(message, event.getPlayer());
        } else {
            event.getPlayerList().getPlayers().forEach(player -> NetworkHandler.sendToClientPlayer(message, player));
        }
    }

    private static void logDatapackSyncCache(OnDatapackSyncEvent event, Map<DataType, Map<Identifier, String>> cache) {
        String target = event.getPlayer() != null
                ? event.getPlayer().getName().getString()
                : "all players: " + event.getPlayerList().getPlayers().size();
        int recipes = countNetworkCache(cache, DataType.RECIPES);
        int blocks = countNetworkCache(cache, DataType.BLOCK_INDEX);
        GunMod.LOGGER.info("TACZ datapack sync to {}: types={} recipes={} blockIndex={} gunIndex={} ammoIndex={} attachmentIndex={}",
                target, cache.size(), recipes, blocks,
                countNetworkCache(cache, DataType.GUN_INDEX),
                countNetworkCache(cache, DataType.AMMO_INDEX),
                countNetworkCache(cache, DataType.ATTACHMENT_INDEX));
        if (recipes == 0 || blocks == 0) {
            GunMod.LOGGER.warn("TACZ datapack sync has incomplete gun smith cache: recipes={} blockIndex={}", recipes, blocks);
        }
    }

    private static int countNetworkCache(Map<DataType, Map<Identifier, String>> cache, DataType type) {
        Map<Identifier, String> values = cache.get(type);
        return values == null ? 0 : values.size();
    }

    public static void reloadAllPack() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        PackRepository packrepository = server.getPackRepository();
        packrepository.reload();

        Collection<String> collection = packrepository.getSelectedIds();
        server.reloadResources(collection);
    }
}
