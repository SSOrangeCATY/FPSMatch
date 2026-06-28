package com.tacz.guns.resource.manager;

import com.tacz.guns.resource.network.DataType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.Map;

public interface INetworkCacheReloadListener extends PreparableReloadListener {
    Map<Identifier, String> getNetworkCache();

    DataType getType();
}
