package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;
import java.util.UUID;

public final class CompletedUpload implements SeekableByteChannel {
    @FunctionalInterface
    interface CloseAction {
        void close() throws IOException;
    }

    private final UUID uploadId;
    private final UploadOwnerScope ownerScope;
    private final long length;
    private final Sha256 expectedHash;
    private final SeekableByteChannel delegate;
    private final CloseAction closeAction;
    private final Object closeLock = new Object();
    private volatile boolean readable;
    private volatile boolean closed;
    private boolean closeStarted;
    private boolean closeComplete;
    private Throwable closeFailure;

    CompletedUpload(
            UUID uploadId,
            UploadOwnerScope ownerScope,
            long length,
            Sha256 expectedHash,
            SeekableByteChannel delegate,
            boolean readable,
            CloseAction closeAction
    ) {
        this.uploadId = Objects.requireNonNull(uploadId, "uploadId");
        this.ownerScope = Objects.requireNonNull(ownerScope, "ownerScope");
        if (length <= 0) {
            throw new IllegalArgumentException("Completed upload length must be positive");
        }
        this.length = length;
        this.expectedHash = Objects.requireNonNull(expectedHash, "expectedHash");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.readable = readable;
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    public UUID uploadId() {
        return uploadId;
    }

    public UploadOwnerScope ownerScope() {
        return ownerScope;
    }

    public long length() {
        return length;
    }

    public Sha256 expectedHash() {
        return expectedHash;
    }

    @Override
    public synchronized int read(ByteBuffer destination) throws IOException {
        requireReadable();
        return delegate.read(Objects.requireNonNull(destination, "destination"));
    }

    @Override
    public synchronized int write(ByteBuffer source) throws IOException {
        Objects.requireNonNull(source, "source");
        requireReadable();
        throw new NonWritableChannelException();
    }

    @Override
    public synchronized long position() throws IOException {
        requireReadable();
        return delegate.position();
    }

    @Override
    public synchronized CompletedUpload position(long nextPosition) throws IOException {
        requireReadable();
        delegate.position(nextPosition);
        return this;
    }

    @Override
    public synchronized long size() throws IOException {
        requireReadable();
        return delegate.size();
    }

    @Override
    public synchronized CompletedUpload truncate(long size) throws IOException {
        requireReadable();
        throw new NonWritableChannelException();
    }

    @Override
    public boolean isOpen() {
        return readable && !closed && delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        boolean ownsClose = false;
        // Preserve monitor admission for legacy callers without invoking state cleanup under it.
        synchronized (this) {
            synchronized (closeLock) {
                if (!closeStarted) {
                    closeStarted = true;
                    closed = true;
                    readable = false;
                    ownsClose = true;
                }
            }
        }

        if (!ownsClose) {
            rethrowCloseFailure(awaitCloseCompletion());
            return;
        }

        Throwable failure = null;
        try {
            delegate.close();
        } catch (Throwable exception) {
            failure = exception;
        }
        try {
            closeAction.close();
        } catch (Throwable exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        } finally {
            synchronized (closeLock) {
                closeFailure = failure;
                closeComplete = true;
                closeLock.notifyAll();
            }
        }

        rethrowCloseFailure(failure);
    }

    void activate() throws IOException {
        synchronized (this) {
            requireManagementOpen();
            delegate.position(0);
            readable = true;
        }
    }

    boolean hasExpectedLength() throws IOException {
        synchronized (this) {
            requireManagementOpen();
            return delegate.size() == length;
        }
    }

    boolean hasExpectedHash() throws IOException {
        synchronized (this) {
            requireManagementOpen();
            delegate.position(0);
            try {
                return expectedHash.equals(Sha256Digest.of(new ChannelInputStream(delegate)));
            } finally {
                if (delegate.isOpen()) {
                    delegate.position(0);
                }
            }
        }
    }

    private void requireReadable() throws ClosedChannelException {
        if (closed || !delegate.isOpen()) {
            throw new ClosedChannelException();
        }
        if (!readable) {
            throw new NonReadableChannelException();
        }
    }

    private void requireManagementOpen() throws ClosedChannelException {
        if (closed || !delegate.isOpen()) {
            throw new ClosedChannelException();
        }
    }

    private Throwable awaitCloseCompletion() {
        boolean interrupted = false;
        Throwable failure;
        synchronized (closeLock) {
            while (!closeComplete) {
                try {
                    closeLock.wait();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            failure = closeFailure;
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return failure;
    }

    private static void rethrowCloseFailure(Throwable failure) throws IOException {
        if (failure == null) {
            return;
        }
        if (failure instanceof IOException exception) {
            throw exception;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IOException("Unable to close completed upload", failure);
    }

    private static final class ChannelInputStream extends java.io.InputStream {
        private final SeekableByteChannel channel;
        private final ByteBuffer singleByte = ByteBuffer.allocate(1);

        private ChannelInputStream(SeekableByteChannel channel) {
            this.channel = channel;
        }

        @Override
        public int read() throws IOException {
            singleByte.clear();
            int count = channel.read(singleByte);
            if (count < 0) {
                return -1;
            }
            if (count == 0) {
                throw new IOException("Completed upload hash read made no progress");
            }
            return singleByte.get(0) & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            int count = channel.read(ByteBuffer.wrap(bytes, offset, length));
            if (count == 0) {
                throw new IOException("Completed upload hash read made no progress");
            }
            return count;
        }
    }
}
