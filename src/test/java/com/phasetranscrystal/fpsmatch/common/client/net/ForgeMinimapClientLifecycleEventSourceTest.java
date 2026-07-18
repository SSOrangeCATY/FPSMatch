package com.phasetranscrystal.fpsmatch.common.client.net;

import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapBootstrap;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ForgeMinimapClientLifecycleEventSourceTest {
    @Test
    void forwardsServiceConnectionIdentityLogoutAndReset() {
        RecordingEvents events = new RecordingEvents();
        ForgeMinimapClientLifecycleEventSource source =
                new ForgeMinimapClientLifecycleEventSource(
                        events, ignored -> "remote/server-a"
                );
        ClientMinimapBootstrap.EventSource serviceEvents = assertInstanceOf(
                ClientMinimapBootstrap.EventSource.class, source
        );
        List<String> calls = new ArrayList<>();
        AtomicInteger resets = new AtomicInteger();
        serviceEvents.bind(
                identity -> calls.add("connect:" + identity),
                () -> calls.add("disconnect"),
                resets::incrementAndGet
        );
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);

        events.login(connection);
        events.reset();
        events.logout(connection);

        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("connect:remote/server-a", "disconnect"), calls
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, resets.get());
    }

    private static final class RecordingEvents
            implements ForgeMinimapClientLifecycleEventSource.EventRegistrar {
        private Consumer<Connection> loggedIn;
        private Consumer<Connection> loggedOut;
        private Runnable reset;

        @Override
        public void onLoggedIn(Consumer<Connection> listener) {
            loggedIn = listener;
        }

        @Override
        public void onLoggedOut(Consumer<Connection> listener) {
            loggedOut = listener;
        }

        @Override
        public void onReset(Runnable listener) {
            reset = listener;
        }

        private void login(Connection connection) {
            loggedIn.accept(connection);
        }

        private void logout(Connection connection) {
            loggedOut.accept(connection);
        }

        private void reset() {
            reset.run();
        }
    }
}
