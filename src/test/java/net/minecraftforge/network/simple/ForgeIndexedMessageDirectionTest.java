package net.minecraftforge.network.simple;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeIndexedMessageDirectionTest {

    @Test
    void forgeRejectsWrongDirectionBeforeDecoderAndConsumer() throws Exception {
        IndexedMessageCodec codec = new IndexedMessageCodec();
        AtomicInteger decoderCalls = new AtomicInteger();
        AtomicInteger consumerCalls = new AtomicInteger();
        codec.addCodecIndex(
                7,
                ProbeMessage.class,
                null,
                payload -> {
                    decoderCalls.incrementAndGet();
                    return new ProbeMessage(payload.readUnsignedByte());
                },
                (message, context) -> consumerCalls.incrementAndGet(),
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        ConnectionHarness connection = connection(PacketFlow.CLIENTBOUND);
        NetworkEvent.Context context = context(
                connection.connection,
                NetworkDirection.PLAY_TO_CLIENT
        );
        FriendlyByteBuf payload = payload(7, 42);

        assertThrows(
                IllegalStateException.class,
                () -> codec.consume(payload, Integer.MIN_VALUE, () -> context)
        );

        assertEquals(0, decoderCalls.get());
        assertEquals(0, consumerCalls.get());
        assertFalse(connection.channel.isOpen());
        assertFalse(connection.connection.isConnected());
        payload.release();
    }

    @Test
    void forgeInvokesDecoderAndConsumerOnceForCorrectDirection() throws Exception {
        IndexedMessageCodec codec = new IndexedMessageCodec();
        AtomicInteger decoderCalls = new AtomicInteger();
        AtomicInteger consumerCalls = new AtomicInteger();
        codec.addCodecIndex(
                8,
                ProbeMessage.class,
                null,
                payload -> {
                    decoderCalls.incrementAndGet();
                    return new ProbeMessage(payload.readUnsignedByte());
                },
                (message, context) -> {
                    assertEquals(43, message.value());
                    consumerCalls.incrementAndGet();
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        ConnectionHarness connection = connection(PacketFlow.SERVERBOUND);
        NetworkEvent.Context context = context(
                connection.connection,
                NetworkDirection.PLAY_TO_SERVER
        );
        FriendlyByteBuf payload = payload(8, 43);

        codec.consume(payload, Integer.MIN_VALUE, () -> context);

        assertEquals(1, decoderCalls.get());
        assertEquals(1, consumerCalls.get());
        assertTrue(connection.channel.isOpen());
        payload.release();
        connection.channel.finishAndReleaseAll();
    }

    private static FriendlyByteBuf payload(int discriminator, int value) {
        FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.buffer());
        payload.writeByte(discriminator);
        payload.writeByte(value);
        return payload;
    }

    private static ConnectionHarness connection(PacketFlow receiving)
            throws ReflectiveOperationException {
        Connection connection = new Connection(receiving);
        EmbeddedChannel channel = new EmbeddedChannel();
        Field channelField = Connection.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(connection, channel);
        return new ConnectionHarness(connection, channel);
    }

    private static NetworkEvent.Context context(
            Connection connection,
            NetworkDirection direction
    ) throws ReflectiveOperationException {
        Constructor<NetworkEvent.Context> constructor =
                NetworkEvent.Context.class.getDeclaredConstructor(
                        Connection.class,
                        NetworkDirection.class,
                        int.class
                );
        constructor.setAccessible(true);
        return constructor.newInstance(connection, direction, Integer.MIN_VALUE);
    }

    private record ProbeMessage(int value) {
    }

    private record ConnectionHarness(
            Connection connection,
            EmbeddedChannel channel
    ) {
    }
}
