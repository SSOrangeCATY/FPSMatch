package com.phasetranscrystal.fpsmatch.core.capability;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.phasetranscrystal.fpsmatch.common.capability.map.MinimapCapability;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapCapabilityTest {
    private static final MinimapCapability.Binding BINDING =
            new MinimapCapability.Binding(
                    NamespacedId.parse("minecraft:overworld"),
                    NamespacedId.parse("fpsmatch:test_map"),
                    12,
                    Sha256.parse("1".repeat(64)),
                    Sha256.parse("2".repeat(64))
            );

    @BeforeAll
    static void registerCapability() {
        if (!FPSMCapabilityManager.isRegistered(MinimapCapability.class)) {
            MinimapCapability.register();
        }
    }

    @Test
    void registrationDoesNotMountTheCapabilityOnEveryMap() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);

        assertFalse(capabilities.contains(MinimapCapability.class));
        assertFalse(FPSMCapabilityManager.getFactory(MinimapCapability.class)
                .orElseThrow().isOriginal());

        assertTrue(capabilities.add(MinimapCapability.class));
        assertTrue(capabilities.contains(MinimapCapability.class));
    }

    @Test
    void persistenceContainsOnlyPublishedMapReferences() {
        CapabilityMap<BaseMap, MapCapability> source =
                CapabilityMap.ofMapCapability(null);
        assertTrue(source.add(MinimapCapability.class));
        MinimapCapability capability = source.get(MinimapCapability.class).orElseThrow();
        capability.write(BINDING);

        JsonElement persisted = capability.toJson();
        CapabilityMap<BaseMap, MapCapability> restored =
                CapabilityMap.ofMapCapability(null);
        restored.write(java.util.Map.of(
                MinimapCapability.class.getSimpleName(), persisted
        ));

        assertEquals(BINDING, restored.get(MinimapCapability.class)
                .orElseThrow().read());
        assertEquals("12", persisted.getAsJsonObject().get("revision").getAsString());
        assertEquals("fpsmatch:test_map",
                persisted.getAsJsonObject().get("documentId").getAsString());
        assertFalse(persisted.toString().contains("image"));
        assertFalse(persisted.toString().contains("png"));
        assertFalse(persisted.toString().contains("bytes"));
    }

    @Test
    void writeAndBindingRejectNullReferences() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        assertTrue(capabilities.add(MinimapCapability.class));
        MinimapCapability capability = capabilities.get(MinimapCapability.class).orElseThrow();

        assertThrows(NullPointerException.class, () -> capability.write(null));
        assertThrows(NullPointerException.class, () -> new MinimapCapability.Binding(
                null, BINDING.documentId(), 0, BINDING.sourceHash(), BINDING.runtimeHash()
        ));
        assertThrows(NullPointerException.class, () -> new MinimapCapability.Binding(
                BINDING.dimension(), null, 0, BINDING.sourceHash(), BINDING.runtimeHash()
        ));
        assertThrows(NullPointerException.class, () -> new MinimapCapability.Binding(
                BINDING.dimension(), BINDING.documentId(), 0, null, BINDING.runtimeHash()
        ));
        assertThrows(NullPointerException.class, () -> new MinimapCapability.Binding(
                BINDING.dimension(), BINDING.documentId(), 0, BINDING.sourceHash(), null
        ));
    }

    @Test
    void invalidEmptyPersistenceValuesDoNotReplaceAnExistingBinding() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        assertTrue(capabilities.add(MinimapCapability.class));
        MinimapCapability capability = capabilities.get(MinimapCapability.class).orElseThrow();
        capability.write(BINDING);

        capabilities.write(java.util.Map.of(
                MinimapCapability.class.getSimpleName(),
                JsonNull.INSTANCE
        ));
        assertEquals(BINDING, capability.read());

        capabilities.write(java.util.Map.of(
                MinimapCapability.class.getSimpleName(),
                new JsonPrimitive("")
        ));
        assertEquals(BINDING, capability.read());
        assertTrue(capabilities.contains(MinimapCapability.class));
    }

    @Test
    void unboundMountedCapabilityIsInactiveUntilBindingWritten() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        assertTrue(capabilities.add(MinimapCapability.class));
        MinimapCapability capability = capabilities.get(MinimapCapability.class).orElseThrow();
        assertFalse(capability.isPublished());
        assertTrue(capability.binding().isEmpty());

        capability.write(BINDING);
        assertTrue(capability.isPublished());
        assertEquals(BINDING, capability.binding().orElseThrow());
    }

    @Test
    void clearBindingDeactivatesWithoutRemovingMount() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);
        assertTrue(capabilities.add(MinimapCapability.class));
        MinimapCapability capability = capabilities.get(MinimapCapability.class).orElseThrow();
        capability.write(BINDING);
        capability.clearBinding();
        assertFalse(capability.isPublished());
        assertTrue(capabilities.contains(MinimapCapability.class));
    }
}