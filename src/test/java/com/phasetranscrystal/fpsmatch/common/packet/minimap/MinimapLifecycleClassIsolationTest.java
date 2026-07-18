package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MinimapLifecycleClassIsolationTest {

    @Test
    void dedicatedLifecyclePathHasNoClientClassReferences() throws IOException {
        for (Class<?> type : List.of(
                MinimapPacketLifecycle.class,
                MinimapPacketEndpointLifecycle.class,
                MinimapPacketEndpointRuntime.class,
                ForgeMinimapServerLifecycleEventSource.class,
                MinimapPacketRegistration.class,
                MinimapC2SPacket.class,
                MinimapS2CPacket.class
        )) {
            String constantPool = classBytes(type);
            assertFalse(
                    constantPool.contains("net/minecraft/client"),
                    type.getName()
            );
            assertFalse(
                    constantPool.contains("net/minecraftforge/client"),
                    type.getName()
            );
            assertFalse(
                    constantPool.contains(
                            "com/phasetranscrystal/fpsmatch/common/client"
                    ),
                    type.getName()
            );
        }
    }

    private static String classBytes(Class<?> type) throws IOException {
        String resource = type.getSimpleName() + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            assertNotNull(input, type.getName());
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
