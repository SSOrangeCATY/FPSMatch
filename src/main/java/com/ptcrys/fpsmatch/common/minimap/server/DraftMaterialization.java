package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, fully revalidated projection of the ACKed draft prefix. */
public final class DraftMaterialization {
    private final DraftState state;
    private final List<Operation> operations;
    private final Map<Sha256, byte[]> referencedContent;

    DraftMaterialization(
            DraftState state,
            List<Operation> operations,
            Map<Sha256, byte[]> referencedContent
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        this.referencedContent = copyContent(referencedContent);
    }

    public DraftState state() {
        return state;
    }

    public List<Operation> operations() {
        return operations;
    }

    public Map<Sha256, byte[]> referencedContent() {
        return copyContent(referencedContent);
    }

    private static Map<Sha256, byte[]> copyContent(Map<Sha256, byte[]> source) {
        Objects.requireNonNull(source, "referencedContent");
        Map<Sha256, byte[]> copy = new LinkedHashMap<>();
        source.forEach((hash, bytes) -> copy.put(
                Objects.requireNonNull(hash, "content hash"),
                Objects.requireNonNull(bytes, "content bytes").clone()
        ));
        return Collections.unmodifiableMap(copy);
    }

    public static final class Operation {
        private final long sequence;
        private final Sha256 descriptorHash;
        private final byte[] descriptorBytes;
        private final List<Sha256> referencedContentHashes;

        Operation(
                long sequence,
                Sha256 descriptorHash,
                byte[] descriptorBytes,
                List<Sha256> referencedContentHashes
        ) {
            if (sequence <= 0) {
                throw new IllegalArgumentException("Draft operation sequence must be positive");
            }
            this.sequence = sequence;
            this.descriptorHash = Objects.requireNonNull(descriptorHash, "descriptorHash");
            this.descriptorBytes = Objects.requireNonNull(
                    descriptorBytes, "descriptorBytes"
            ).clone();
            this.referencedContentHashes = List.copyOf(Objects.requireNonNull(
                    referencedContentHashes, "referencedContentHashes"
            ));
        }

        public long sequence() {
            return sequence;
        }

        public Sha256 descriptorHash() {
            return descriptorHash;
        }

        public byte[] descriptorBytes() {
            return descriptorBytes.clone();
        }

        public List<Sha256> referencedContentHashes() {
            return referencedContentHashes;
        }
    }
}
