package com.tacz.guns.api;

import com.tacz.guns.api.client.other.IThirdPersonAnimation;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.util.GunSmithTableBlockIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TimelessAPI {
    public static Optional<GunDisplayInstance> getGunDisplay(ItemStack stack) {
        if (stack.getItem() instanceof IGun iGun) {
            Identifier gunId = iGun.getGunId(stack);
            if (getCommonGunIndex(gunId).isEmpty()) {
                return Optional.empty();
            }
            Identifier displayId = iGun.getGunDisplayId(stack);
            if (displayId.equals(DefaultAssets.DEFAULT_GUN_DISPLAY_ID)) {
                return getClientGunIndex(gunId).map(ClientGunIndex::getDefaultDisplay);
            } else {
                return getGunDisplay(displayId, gunId);
            }
        }
        return Optional.empty();
    }

    public static Optional<ClientGunIndex> getClientGunIndex(Identifier gunId) {
        return Optional.ofNullable(ClientIndexManager.GUN_INDEX.get(gunId));
    }

    public static Optional<GunDisplayInstance> getGunDisplay(Identifier displayId, Identifier fallbackGunId) {
        if (displayId == null || displayId.equals(DefaultAssets.DEFAULT_GUN_DISPLAY_ID)) {
            return getClientGunIndex(fallbackGunId).map(ClientGunIndex::getDefaultDisplay);
        }

        GunDisplayInstance instance = ClientIndexManager.getOrCreateGunDisplay(displayId);
        if (instance == null) {
            return getClientGunIndex(fallbackGunId).map(ClientGunIndex::getDefaultDisplay);
        }
        return Optional.of(instance);
    }

    public static Optional<ClientAttachmentIndex> getClientAttachmentIndex(Identifier attachmentId) {
        return Optional.ofNullable(ClientIndexManager.ATTACHMENT_INDEX.get(attachmentId));
    }

    public static Optional<ClientAmmoIndex> getClientAmmoIndex(Identifier ammoId) {
        return Optional.ofNullable(ClientIndexManager.AMMO_INDEX.get(ammoId));
    }

    public static Optional<ClientBlockIndex> getClientBlockIndex(Identifier blockId) {
        Identifier normalizedBlockId = GunSmithTableBlockIds.normalize(blockId);
        if (normalizedBlockId == null || DefaultAssets.EMPTY_BLOCK_ID.equals(normalizedBlockId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ClientIndexManager.BLOCK_INDEX.get(normalizedBlockId));
    }

    public static Set<Map.Entry<Identifier, ClientGunIndex>> getAllClientGunIndex() {
        return ClientIndexManager.getAllGuns();
    }

    public static Set<Map.Entry<Identifier, ClientAmmoIndex>> getAllClientAmmoIndex() {
        return ClientIndexManager.getAllAmmo();
    }

    public static Set<Map.Entry<Identifier, ClientAttachmentIndex>> getAllClientAttachmentIndex() {
        return ClientIndexManager.getAllAttachments();
    }

    public static Optional<CommonBlockIndex> getCommonBlockIndex(Identifier blockId) {
        Identifier normalizedBlockId = GunSmithTableBlockIds.normalize(blockId);
        if (normalizedBlockId == null || DefaultAssets.EMPTY_BLOCK_ID.equals(normalizedBlockId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(CommonAssetsManager.get().getBlockIndex(normalizedBlockId));
    }

    public static Optional<CommonGunIndex> getCommonGunIndex(Identifier gunId) {
        return Optional.ofNullable(CommonAssetsManager.get().getGunIndex(gunId));
    }

    public static Optional<CommonAttachmentIndex> getCommonAttachmentIndex(Identifier attachmentId) {
        return Optional.ofNullable(CommonAssetsManager.get().getAttachmentIndex(attachmentId));
    }

    public static Optional<CommonAmmoIndex> getCommonAmmoIndex(Identifier ammoId) {
        return Optional.ofNullable(CommonAssetsManager.get().getAmmoIndex(ammoId));
    }

    /**
     * @deprecated
     * 不再使用独立的配方同步，而是使用原版的配方加载器<br/>
     * 请用 {@link net.minecraft.world.item.crafting.RecipeManager#byKey(Identifier)}和{@link net.minecraft.world.item.crafting.RecipeManager#getAllRecipesFor(RecipeType)}获取配方
     */
    @Deprecated
    public static Optional<GunSmithTableRecipe> getRecipe(Identifier recipeId) {
        GunSmithTableRecipe cachedRecipe = CommonNetworkCache.INSTANCE.getRecipe(recipeId);
        if (cachedRecipe != null) {
            return Optional.of(cachedRecipe);
        }
        CommonAssetsManager assetsManager = CommonAssetsManager.getInstance();
        if (assetsManager != null && assetsManager.recipeManager != null) {
            return assetsManager.recipeManager.getRecipes().stream()
                    .filter(holder -> holder.id().identifier().equals(recipeId))
                    .map(holder -> holder.value())
                    .filter(recipe -> recipe.getType() == ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())
                    .filter(recipe -> recipe instanceof GunSmithTableRecipe)
                    .map(recipe -> (GunSmithTableRecipe) recipe)
                    .findFirst();
        }
        return Optional.empty();
    }

    public static Set<Map.Entry<Identifier, CommonBlockIndex>> getAllCommonBlockIndex() {
        return CommonAssetsManager.get().getAllBlocks();
    }

    public static Set<Map.Entry<Identifier, CommonGunIndex>> getAllCommonGunIndex() {
        return CommonAssetsManager.get().getAllGuns();
    }

    public static Set<Map.Entry<Identifier, CommonAmmoIndex>> getAllCommonAmmoIndex() {
        return CommonAssetsManager.get().getAllAmmos();
    }

    public static Set<Map.Entry<Identifier, CommonAttachmentIndex>> getAllCommonAttachmentIndex() {
        return CommonAssetsManager.get().getAllAttachments();
    }

    /**
     * @deprecated
     * 不再使用独立的配方同步，而是使用原版的配方加载器<br/>
     * 请用 {@link net.minecraft.world.item.crafting.RecipeManager#byKey(Identifier)}和{@link net.minecraft.world.item.crafting.RecipeManager#getAllRecipesFor(RecipeType)}获取配方
     */
    @Deprecated
    public static Map<Identifier, GunSmithTableRecipe> getAllRecipes() {
        Map<Identifier, GunSmithTableRecipe> networkRecipes = new LinkedHashMap<>();
        CommonNetworkCache.INSTANCE.getAllRecipes().forEach(entry -> networkRecipes.put(entry.getKey(), entry.getValue()));
        Map<Identifier, GunSmithTableRecipe> managerRecipes = new LinkedHashMap<>();
        CommonAssetsManager assetsManager = CommonAssetsManager.getInstance();
        if (assetsManager != null && assetsManager.recipeManager != null) {
            assetsManager.recipeManager.getRecipes().stream()
                    .filter(holder -> holder.value().getType() == ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())
                    .filter(holder -> holder.value() instanceof GunSmithTableRecipe)
                    .forEach(holder -> managerRecipes.put(holder.id().identifier(), (GunSmithTableRecipe) holder.value()));
        }
        if (displayableRecipeCount(managerRecipes) > displayableRecipeCount(networkRecipes)) {
            return managerRecipes;
        }
        return networkRecipes;
    }

    private static int displayableRecipeCount(Map<Identifier, GunSmithTableRecipe> recipes) {
        int count = 0;
        for (GunSmithTableRecipe recipe : recipes.values()) {
            Identifier group = recipe.getResult().getGroup();
            if (!recipe.getResult().getResult().isEmpty() && group != null && !TabConfig.TAB_EMPTY.equals(group)) {
                count++;
            }
        }
        return count;
    }

    public static void registerThirdPersonAnimation(String name, IThirdPersonAnimation animation) {
        ThirdPersonManager.register(name, animation);
    }
}
