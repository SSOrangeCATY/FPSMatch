package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;

final class EditorDocumentIdentity {
    private EditorDocumentIdentity() {
    }

    static NamespacedId forMap(MapKey mapKey) {
        Objects.requireNonNull(mapKey, "mapKey");
        String gameType = mapKey.gameType().toLowerCase().replace(':', '/');
        return new NamespacedId(
                "fpsmatch", "minimap/" + gameType + "/" + slug(mapKey.mapName())
        );
    }

    private static String slug(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = Character.toLowerCase(value.charAt(index));
            if (isSafe(current)) {
                result.append(current);
            } else {
                result.append('_');
            }
        }
        return result.length() == 0 ? "map" : result.toString();
    }

    private static boolean isSafe(char value) {
        return value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '_' || value == '-' || value == '.' || value == '/';
    }
}
