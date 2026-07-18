package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;

@FunctionalInterface
public interface MinimapTextureResolver {
    Optional<TextureHandle> resolve(String textureKey);

    record TextureHandle(Identifier location, int width, int height) {
        public TextureHandle {
            Objects.requireNonNull(location, "location");
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Texture dimensions must be positive");
            }
        }
    }
}
