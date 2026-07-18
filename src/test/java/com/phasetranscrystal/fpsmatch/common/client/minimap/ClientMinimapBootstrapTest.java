package com.phasetranscrystal.fpsmatch.common.client.minimap;

import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapDiskCache;
import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapS2CDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientMinimapBootstrapTest {
    @TempDir
    Path temp;

    @Test
    void installsDispatcherAndBindsConnectionResetLifecycleOnce() {
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                message -> {},
                () -> 0L,
                UUID::randomUUID
        );
        RecordingEvents events = new RecordingEvents();
        AtomicReference<MinimapS2CDispatcher> installed = new AtomicReference<>();
        ClientMinimapBootstrap bootstrap = new ClientMinimapBootstrap(
                services, installed::set
        );

        bootstrap.install(events);
        bootstrap.install(events);

        assertSame(services.dispatcher(), installed.get());
        assertEquals(1, events.bindCount);
        events.connect.accept("remote/abc");
        assertFalse(services.subscribe(
                com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity.Scope.EDITOR,
                new com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity.MapTarget(
                        new com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey("cs", "dust2"),
                        com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                                "minecraft:overworld"
                        )
                ),
                java.util.List.of(),
                java.util.Optional.empty()
        ).isPresent());
        events.reset.run();
        events.disconnect.run();
        assertFalse(services.hasActiveScope(
                com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity.Scope.MATCH_HUD
        ));
    }

    private static final class RecordingEvents
            implements ClientMinimapBootstrap.EventSource {
        private Consumer<String> connect;
        private Runnable disconnect;
        private Runnable reset;
        private int bindCount;

        @Override
        public void bind(
                Consumer<String> onConnect,
                Runnable onDisconnect,
                Runnable onReset
        ) {
            bindCount++;
            connect = onConnect;
            disconnect = onDisconnect;
            reset = onReset;
        }
    }
}
