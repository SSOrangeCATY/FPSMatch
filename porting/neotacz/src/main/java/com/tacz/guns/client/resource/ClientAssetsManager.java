package com.tacz.guns.client.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.animation.gltf.AnimationStructure;
import com.tacz.guns.api.vmlib.LuaAnimationConstant;
import com.tacz.guns.api.vmlib.LuaGunAnimationConstant;
import com.tacz.guns.api.vmlib.LuaLibrary;
import com.tacz.guns.client.resource.manager.DisplayManager;
import com.tacz.guns.client.resource.manager.GltfManager;
import com.tacz.guns.client.resource.manager.PackInfoManager;
import com.tacz.guns.client.resource.pojo.CommonTransformObject;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.client.resource.pojo.animation.bedrock.AnimationKeyframes;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.guns.client.resource.pojo.animation.bedrock.SoundEffectKeyframes;
import com.tacz.guns.client.resource.pojo.display.ammo.AmmoDisplay;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.resource.pojo.display.block.BlockDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.CubesItem;
import com.tacz.guns.client.resource.serialize.AnimationKeyframesSerializer;
import com.tacz.guns.client.resource.serialize.ItemStackSerializer;
import com.tacz.guns.client.resource.serialize.SoundEffectKeyframesSerializer;
import com.tacz.guns.client.resource.serialize.Vector3fSerializer;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.manager.LazyJsonDataManager;
import com.tacz.guns.resource.manager.ScriptManager;
import com.tacz.guns.resource.serialize.IdentifierSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.luaj.vm2.LuaTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 客户端资源管理器<br/>
 * 所有枪包资源缓存在此
 */
public enum ClientAssetsManager {
    INSTANCE;
    public static final Gson GSON = new GsonBuilder().registerTypeAdapter(Identifier.class, new IdentifierSerializer())
            .registerTypeAdapter(CubesItem.class, new CubesItem.Deserializer())
            .registerTypeAdapter(Vector3f.class, new Vector3fSerializer())
            .registerTypeAdapter(CommonTransformObject.class, new CommonTransformObject.Serializer())
            .registerTypeAdapter(ItemStack.class, new ItemStackSerializer())
            .registerTypeAdapter(AnimationKeyframes.class, new AnimationKeyframesSerializer())
            .registerTypeAdapter(SoundEffectKeyframes.class, new SoundEffectKeyframesSerializer())
            .registerTypeAdapter(ItemTransforms.class, new ItemTransforms.Deserializer())
            .registerTypeAdapter(ItemTransform.class, new ItemTransform.Deserializer())
            .create();

    // 枪械展示数据
    private DisplayManager<GunDisplay> gunDisplay;
    // 弹药展示数据
    private DisplayManager<AmmoDisplay> ammoDisplay;
    // 配件展示数据
    private DisplayManager<AttachmentDisplay> attachmentDisplay;
    // 方块展示数据
    private DisplayManager<BlockDisplay> blockDisplay;
    // 原始基岩版模型
    private LazyJsonDataManager<BedrockModelPOJO> bedrockModel;
    // 基岩版模型动画
    private LazyJsonDataManager<BedrockAnimationFile> bedrockAnimation;
    // gltf 动画
    private GltfManager gltfAnimation;
    // 客户端脚本
    private final List<LuaLibrary> libList = List.of(new LuaAnimationConstant(), new LuaGunAnimationConstant());
    private ScriptManager scriptManager;
    // 音效
    // 枪包元数据
    private PackInfoManager packInfo;

    private List<ReloadListenerEntry> listeners;

    public void reloadAndRegister(BiConsumer<Identifier, PreparableReloadListener> register) {
        if (listeners == null) {
            listeners = new ArrayList<>();
            gunDisplay = register("client/gun_display", new DisplayManager<>(GunDisplay.class, GSON, "display/guns", "GunDisplayLoader"));
            ammoDisplay = register("client/ammo_display", new DisplayManager<>(AmmoDisplay.class, GSON, "display/ammo", "AmmoDisplayLoader"));
            attachmentDisplay = register("client/attachment_display", new DisplayManager<>(AttachmentDisplay.class, GSON, "display/attachments", "AttachmentDisplayLoader"));
            blockDisplay = register("client/block_display", new DisplayManager<>(BlockDisplay.class, GSON, "display/blocks", "BlockDisplayLoader"));

            bedrockModel = register("client/bedrock_model", new LazyJsonDataManager<>(BedrockModelPOJO.class, GSON, "geo_models", "BedrockModelLoader",
                    id -> GunMod.MOD_ID.equals(id.getNamespace())));
            bedrockAnimation = register("client/bedrock_animation", new LazyJsonDataManager<>(BedrockAnimationFile.class, GSON, new FileToIdConverter("animations", ".animation.json"),
                    "BedrockAnimationLoader", id -> GunMod.MOD_ID.equals(id.getNamespace())));
            gltfAnimation = register("client/gltf_animation", new GltfManager());
            scriptManager = register("client/scripts", new ScriptManager(new FileToIdConverter("scripts", ".lua"), libList));
            packInfo = register("client/pack_info", new PackInfoManager());
            register("client/index_reload", new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                    return null;
                }

                @Override
                protected void apply(Void preparations, ResourceManager manager, ProfilerFiller profiler) {
                    ClientIndexManager.reload();
                }
            });
        }
        listeners.forEach(entry -> register.accept(entry.id(), entry.listener()));
    }

    private <T extends PreparableReloadListener> T register(String path, T listener) {
        listeners.add(new ReloadListenerEntry(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, path), listener));
        return listener;
    }

    private record ReloadListenerEntry(Identifier id, PreparableReloadListener listener) {
    }

    @Nullable
    public GunDisplay getGunDisplay(Identifier id) {
        return gunDisplay.getData(id);
    }

    public Set<Map.Entry<Identifier, GunDisplay>> getGunDisplays() {
        return gunDisplay.getAllData().entrySet();
    }

    public Set<Identifier> getGunDisplayIds() {
        return gunDisplay.getAllData().keySet();
    }

    @Nullable
    public AttachmentDisplay getAttachmentDisplay(Identifier id) {
        return attachmentDisplay.getData(id);
    }

    @Nullable
    public AmmoDisplay getAmmoDisplay(Identifier id) {
        return ammoDisplay.getData(id);
    }

    @Nullable
    public BlockDisplay getBlockDisplay(Identifier id) {
        return blockDisplay.getData(id);
    }

    @Nullable
    public BedrockModelPOJO getBedrockModelPOJO(Identifier id) {
        return bedrockModel.getData(id);
    }

    @Nullable
    public BedrockAnimationFile getBedrockAnimations(Identifier id) {
        return bedrockAnimation.getData(id);
    }

    @Nullable
    public LuaTable getScript(Identifier id) {
        return scriptManager.getScript(id);
    }

    @Nullable
    public AnimationStructure getGltfAnimation(Identifier id) {
        return gltfAnimation.getGltfAnimation(id);
    }

    @Nullable
    public PackInfo getPackInfo(String namespace) {
        return packInfo.getData(namespace);
    }

    @Nullable
    public PackInfo getPackInfo(@Nullable Identifier namespace) {
        if (namespace == null) {
            return null;
        }
        return packInfo.getData(namespace.getNamespace());
    }

    public static void reloadAllPack() {
        try {
            Minecraft.getInstance().reloadResourcePacks().get();
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                // 直接刷新data
                CommonAssetsManager.reloadAllPack();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
