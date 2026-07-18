package com.phasetranscrystal.fpsmatch.core.capability;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityMapPersistenceTest {
    @BeforeAll
    static void registerFixtureCapability() {
        if (!FPSMCapabilityManager.isRegistered(RestorableCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    RestorableCapability.class,
                    RestorableCapability::new
            );
        }
        if (!FPSMCapabilityManager.isRegistered(NonSavableCapability.class)) {
            FPSMCapabilityManager.register(
                    FPSMCapabilityManager.CapabilityType.MAP,
                    NonSavableCapability.class,
                    NonSavableCapability::new
            );
        }
    }

    @Test
    void restoringAnAbsentCapabilityCreatesAndDecodesItInOneWrite() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);

        capabilities.write(Map.of(
                RestorableCapability.class.getSimpleName(),
                new JsonPrimitive("restored")
        ));

        RestorableCapability restored = capabilities.get(RestorableCapability.class)
                .orElseThrow();
        assertTrue(capabilities.contains(RestorableCapability.class));
        assertEquals("restored", restored.read());
    }

    @Test
    void failedDecodeDoesNotInstallANewCapabilityWithNullState() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);

        capabilities.write(Map.of(
                RestorableCapability.class.getSimpleName(),
                new JsonObject()
        ));

        assertFalse(capabilities.contains(RestorableCapability.class));
    }

    @Test
    void jsonNullDoesNotMountAnAbsentSavableCapability() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);

        capabilities.write(Map.of(
                RestorableCapability.class.getSimpleName(),
                JsonNull.INSTANCE
        ));

        assertFalse(capabilities.contains(RestorableCapability.class));
    }

    @Test
    void emptySentinelDoesNotMountAnAbsentSavableCapability() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);

        capabilities.write(Map.of(
                RestorableCapability.class.getSimpleName(),
                new JsonPrimitive("")
        ));

        assertFalse(capabilities.contains(RestorableCapability.class));
    }

    @Test
    void emptySentinelStillMountsAnAbsentNonSavableCapability() {
        CapabilityMap<BaseMap, MapCapability> capabilities =
                CapabilityMap.ofMapCapability(null);

        capabilities.write(Map.of(
                NonSavableCapability.class.getSimpleName(),
                new JsonPrimitive("")
        ));

        assertTrue(capabilities.contains(NonSavableCapability.class));
    }

    private static final class RestorableCapability extends MapCapability
            implements FPSMCapability.Savable<String> {
        private String value;

        private RestorableCapability(BaseMap map) {
            super(map);
        }

        @Override
        public Codec<String> codec() {
            return Codec.STRING;
        }

        @Override
        public String write(String value) {
            this.value = value;
            return value;
        }

        @Override
        public String read() {
            return value;
        }
    }

    private static final class NonSavableCapability extends MapCapability {
        private NonSavableCapability(BaseMap map) {
            super(map);
        }
    }
}
