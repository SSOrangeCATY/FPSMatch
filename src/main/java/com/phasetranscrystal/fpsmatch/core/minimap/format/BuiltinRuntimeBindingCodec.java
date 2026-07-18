package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BuiltinRuntimeBinding;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;
import java.util.Set;
import java.util.Arrays;

public final class BuiltinRuntimeBindingCodec {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "binding", "dimension", "documentId", "runtimeHash"
    );
    private static final Set<String> MAP_FIELDS = Set.of("gameType", "mapName");

    private BuiltinRuntimeBindingCodec() {
    }

    public static BuiltinRuntimeBinding read(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > MinimapHardLimits.MAX_BUILTIN_BINDING_BYTES) {
            throw new ContainerValidationException(
                    "Builtin runtime binding exceeds its byte limit"
            );
        }
        try {
            JsonElement parsed = StrictJsonParser.parse(bytes);
            if (!Arrays.equals(
                    bytes,
                    JcsCanonicalizer.canonicalize(
                            parsed, MinimapHardLimits.MAX_BUILTIN_BINDING_BYTES
                    )
            )) {
                throw new ContainerValidationException(
                        "Builtin runtime binding is not JCS canonical"
                );
            }
            if (!parsed.isJsonObject()) {
                throw new ContainerValidationException(
                        "Builtin runtime binding must be a JSON object"
                );
            }
            JsonObject root = parsed.getAsJsonObject();
            requireFields(root, ROOT_FIELDS, "Builtin runtime binding");
            JsonObject map = object(root, "binding");
            requireFields(map, MAP_FIELDS, "Builtin runtime map binding");
            return new BuiltinRuntimeBinding(
                    new MapKey(string(map, "gameType"), string(map, "mapName")),
                    NamespacedId.parse(string(root, "dimension")),
                    NamespacedId.parse(string(root, "documentId")),
                    Sha256.parse(string(root, "runtimeHash"))
            );
        } catch (ContainerValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ContainerValidationException(
                    "Builtin runtime binding is invalid", exception
            );
        }
    }

    public static byte[] write(BuiltinRuntimeBinding binding) {
        Objects.requireNonNull(binding, "binding");
        JsonObject root = new JsonObject();
        JsonObject map = new JsonObject();
        map.addProperty("gameType", binding.binding().gameType());
        map.addProperty("mapName", binding.binding().mapName());
        root.add("binding", map);
        root.addProperty("dimension", binding.dimension().toString());
        root.addProperty("documentId", binding.documentId().toString());
        root.addProperty("runtimeHash", binding.runtimeHash().value());
        return JcsCanonicalizer.canonicalize(
                root, MinimapHardLimits.MAX_BUILTIN_BINDING_BYTES
        );
    }

    private static void requireFields(
            JsonObject object,
            Set<String> expected,
            String label
    ) {
        if (!object.keySet().equals(expected)) {
            throw new ContainerValidationException(label + " fields are invalid");
        }
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new ContainerValidationException(
                    "Builtin runtime binding object is invalid: " + key
            );
        }
        return value.getAsJsonObject();
    }

    private static String string(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new ContainerValidationException(
                    "Builtin runtime binding string is invalid: " + key
            );
        }
        return value.getAsString();
    }
}
