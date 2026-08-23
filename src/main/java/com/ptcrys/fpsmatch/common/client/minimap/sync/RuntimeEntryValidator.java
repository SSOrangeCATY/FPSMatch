package com.ptcrys.fpsmatch.common.client.minimap.sync;

import com.ptcrys.fpsmatch.common.client.minimap.cache.MinimapCacheKey;

@FunctionalInterface
public interface RuntimeEntryValidator {
    boolean validate(MinimapCacheKey cacheKey, byte[] payload);
}
