package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.io.IOException;
import java.nio.ByteBuffer;
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
    private boolean closed;

    CompletedUpload(
            UUID uploadId,
            UploadOwnerScope ownerScope,
            long length,
            Sha256 expectedHash,
            SeekableByteChannel delegate,
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
    public int read(ByteBuffer destination) throws IOException {
        return delegate.read(Objects.requireNonNull(destination, "destination"));
    }

    @Override
    public int write(ByteBuffer source) throws IOException {
        Objects.requireNonNull(source, "source");
        throw new NonWritableChannelException();
    }

    @Override
    public long position() throws IOException {
        return delegate.position();
    }

    @Override
    public CompletedUpload position(long nextPosition) throws IOException {
        delegate.position(nextPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        return delegate.size();
    }

    @Override
    public CompletedUpload truncate(long size) throws IOException {
        throw new NonWritableChannelException();
    }

    @Override
    public boolean isOpen() {
        return !closed && delegate.isOpen();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            delegate.close();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            closeAction.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
