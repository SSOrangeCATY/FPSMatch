package com.tacz.guns.resource;

import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.block.BlockData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;

import java.util.Map;
import java.util.Set;

public interface ICommonResourceProvider {
    @Nullable GunData getGunData(Identifier id);

    @Nullable AttachmentData getAttachmentData(Identifier attachmentId);

    @Nullable BlockData getBlockData(Identifier id);

    @Nullable RecipeFilter getRecipeFilter(Identifier id);

    @Nullable CommonGunIndex getGunIndex(Identifier gunId);

    @Nullable CommonAmmoIndex getAmmoIndex(Identifier ammoId);

    @Nullable CommonAttachmentIndex getAttachmentIndex(Identifier attachmentId);

    @Nullable CommonBlockIndex getBlockIndex(Identifier blockId);

    @Nullable public LuaTable getScript(Identifier scriptId);

    Set<Map.Entry<Identifier, CommonGunIndex>> getAllGuns();

    Set<Map.Entry<Identifier, CommonAmmoIndex>> getAllAmmos();

    Set<Map.Entry<Identifier, CommonAttachmentIndex>> getAllAttachments();

    Set<Map.Entry<Identifier, CommonBlockIndex>> getAllBlocks();

    Set<String> getAttachmentTags(Identifier registryName);

    Set<String> getAllowAttachmentTags(Identifier registryName);
}
