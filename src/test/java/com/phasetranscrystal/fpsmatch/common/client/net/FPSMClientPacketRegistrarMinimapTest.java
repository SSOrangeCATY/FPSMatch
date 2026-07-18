package com.phasetranscrystal.fpsmatch.common.client.net;

import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapBootstrap;
import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapServices;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapDiskCache;
import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapS2CDispatcher;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireCodec;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class FPSMClientPacketRegistrarMinimapTest {
    @TempDir
    Path temp;

    @Test
    void installsDispatcherAndSendsSubscriptionsThroughTheC2SEnvelope() {
        RecordingEvents events = new RecordingEvents();
        ArrayList<MinimapC2SPacket> packets = new ArrayList<>();
        AtomicReference<MinimapS2CDispatcher> dispatcher = new AtomicReference<>();
        AtomicInteger ids = new AtomicInteger(1);

        ClientMinimapServices services = FPSMClientPacketRegistrar.installMinimap(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                packets::add,
                () -> 0L,
                () -> new UUID(0L, ids.getAndIncrement()),
                events,
                dispatcher::set
        );
        events.connect.accept("remote/server-a");
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(
                new MapKey("cs", "dust2"),
                NamespacedId.parse("minecraft:overworld")
        );

        FPSMClientPacketRegistrar.probeMatchHud(
                services, "cs", "dust2", "minecraft:overworld"
        ).orElseThrow();
        assertEquals(Optional.empty(), FPSMClientPacketRegistrar.probeMatchHud(
                services, "cs", "dust2", "minecraft:overworld"
        ));

        assertSame(services.dispatcher(), dispatcher.get());
        assertEquals(1, packets.size());
        RuntimeWireMessage.Subscribe subscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class,
                MinimapWireCodec.decode(
                        MinimapMessageDirection.C2S,
                        packets.get(0).segment().segmentData()
                )
        );
        assertEquals(target, subscribe.target());
    }

    private static final class RecordingEvents
            implements ClientMinimapBootstrap.EventSource {
        private Consumer<String> connect;

        @Override
        public void bind(
                Consumer<String> onConnect,
                Runnable onDisconnect,
                Runnable onReset
        ) {
            connect = onConnect;
        }
    }
}
