package com.ptcrys.fpsmatch.common.client.minimap.generated;

import java.util.Objects;
import java.util.Optional;

/** Static server tiles always win; generated pixels only fill a missing path. */
public final class GeneratedTileSelection {
    private GeneratedTileSelection() {
    }

    public static Selection staticFirst(
            Optional<String> staticTextureKey,
            Optional<GeneratedMinimapTile> generated
    ) {
        Objects.requireNonNull(staticTextureKey, "staticTextureKey");
        Objects.requireNonNull(generated, "generated");
        return staticTextureKey.isPresent()
                ? new Selection(staticTextureKey, Optional.empty())
                : new Selection(Optional.empty(), generated);
    }

    public record Selection(
            Optional<String> staticTextureKey,
            Optional<GeneratedMinimapTile> generated
    ) {
        public Selection {
            Objects.requireNonNull(staticTextureKey, "staticTextureKey");
            Objects.requireNonNull(generated, "generated");
            if (staticTextureKey.isPresent() && generated.isPresent()) {
                throw new IllegalArgumentException(
                        "Static and generated tile cannot both be selected"
                );
            }
        }

        public boolean usesGenerated() {
            return generated.isPresent();
        }
    }
}
