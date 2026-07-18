package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryFileSystemBoundedReadChannelTest {
    @Test
    void oversizedChannelIsClosedBeforeAnyPayloadRead() {
        ReadTrackingChannel channel = new ReadTrackingChannel(11);

        assertThrows(
                IOException.class,
                () -> RepositoryFileSystem.BoundedReadChannel.acquire(channel, 10)
        );

        assertEquals(0, channel.readCalls());
        assertFalse(channel.isOpen());
    }

    @Test
    void negativeLimitRetainsChannelCloseFailureAsSuppressed() {
        CloseFailingChannel channel = new CloseFailingChannel();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> RepositoryFileSystem.BoundedReadChannel.acquire(channel, -1)
        );

        assertFalse(channel.isOpen());
        assertEquals(1, failure.getSuppressed().length);
        IOException closeFailure = assertInstanceOf(
                IOException.class,
                failure.getSuppressed()[0]
        );
        assertEquals("injected close failure", closeFailure.getMessage());
    }

    private static final class CloseFailingChannel implements SeekableByteChannel {
        private boolean open = true;

        @Override
        public int read(ByteBuffer destination) {
            throw new AssertionError("negative limit must be rejected before reading");
        }

        @Override
        public int write(ByteBuffer source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long position() {
            return 0;
        }

        @Override
        public SeekableByteChannel position(long newPosition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long size() {
            throw new AssertionError("negative limit must be rejected before querying size");
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() throws IOException {
            open = false;
            throw new IOException("injected close failure");
        }
    }

    private static final class ReadTrackingChannel implements SeekableByteChannel {
        private final long size;
        private boolean open = true;
        private int readCalls;

        private ReadTrackingChannel(long size) {
            this.size = size;
        }

        int readCalls() {
            return readCalls;
        }

        @Override
        public int read(ByteBuffer destination) {
            readCalls++;
            return -1;
        }

        @Override
        public int write(ByteBuffer source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long position() {
            return 0;
        }

        @Override
        public SeekableByteChannel position(long newPosition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
