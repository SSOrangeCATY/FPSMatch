package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.WireTransfer;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

final class EditorSourceTransferAccumulator {
    private final UUID requestId;
    private final boolean manifest;
    private final UUID transferId;
    private final int fragmentCount;
    private final long totalLength;
    private final Sha256 objectHash;
    private final byte[][] fragments;

    EditorSourceTransferAccumulator(
            UUID requestId,
            boolean manifest,
            WireTransfer.TransferFragment first
    ) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.manifest = manifest;
        transferId = first.transferId();
        fragmentCount = first.fragmentCount();
        totalLength = first.totalLength();
        objectHash = first.objectHash();
        fragments = new byte[fragmentCount][];
    }

    boolean accept(
            UUID requestId,
            boolean manifest,
            WireTransfer.TransferFragment fragment
    ) {
        if (!this.requestId.equals(requestId) || this.manifest != manifest
                || !transferId.equals(fragment.transferId())
                || fragmentCount != fragment.fragmentCount()
                || totalLength != fragment.totalLength()
                || !objectHash.equals(fragment.objectHash())) {
            throw new EditorCommandException("Editor transfer identity changed mid-stream");
        }
        byte[] previous = fragments[fragment.fragmentIndex()];
        if (previous != null && !Arrays.equals(previous, fragment.fragmentData())) {
            throw new EditorCommandException("Editor transfer fragment was replayed with new bytes");
        }
        fragments[fragment.fragmentIndex()] = fragment.fragmentData();
        return Arrays.stream(fragments).allMatch(Objects::nonNull);
    }

    byte[] bytes() {
        byte[] result = new byte[Math.toIntExact(totalLength)];
        int offset = 0;
        for (byte[] fragment : fragments) {
            System.arraycopy(fragment, 0, result, offset, fragment.length);
            offset += fragment.length;
        }
        if (offset != result.length || !objectHash.equals(Sha256Digest.of(result))) {
            throw new EditorCommandException("Editor transfer hash does not match its fragments");
        }
        return result;
    }
}
