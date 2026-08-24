package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One local gesture with all material required to apply and reverse it. */
public final class EditorEdit {
    private final List<EditorOperation> forward;
    private final List<EditorOperation> inverse;
    private final Map<Sha256, byte[]> payloads;

    public EditorEdit(
            List<EditorOperation> forward,
            List<EditorOperation> inverse,
            Map<Sha256, byte[]> payloads
    ) {
        this.forward = List.copyOf(Objects.requireNonNull(forward, "forward"));
        this.inverse = List.copyOf(Objects.requireNonNull(inverse, "inverse"));
        if (this.forward.isEmpty() || this.inverse.isEmpty()) {
            throw new IllegalArgumentException("Editor edits require forward and inverse operations");
        }
        LinkedHashMap<Sha256, byte[]> owned = new LinkedHashMap<>();
        Objects.requireNonNull(payloads, "payloads").forEach((hash, bytes) -> {
            Objects.requireNonNull(hash, "payload hash");
            Objects.requireNonNull(bytes, "payload bytes");
            byte[] copy = bytes.clone();
            if (!Sha256Digest.of(copy).equals(hash)) {
                throw new IllegalArgumentException("Editor payload does not match its SHA-256 key");
            }
            owned.put(hash, copy);
        });
        this.payloads = Collections.unmodifiableMap(owned);
        validatePutPayloads(this.forward);
        validatePutPayloads(this.inverse);
    }

    public List<EditorOperation> forward() {
        return forward;
    }

    public List<EditorOperation> inverse() {
        return inverse;
    }

    public Map<Sha256, byte[]> payloads() {
        LinkedHashMap<Sha256, byte[]> copy = new LinkedHashMap<>();
        payloads.forEach((hash, bytes) -> copy.put(hash, bytes.clone()));
        return Collections.unmodifiableMap(copy);
    }

    public Optional<byte[]> payload(Sha256 hash) {
        Objects.requireNonNull(hash, "hash");
        byte[] bytes = payloads.get(hash);
        return bytes == null ? Optional.empty() : Optional.of(bytes.clone());
    }

    public EditorEdit reversed() {
        return new EditorEdit(inverse, forward, payloads);
    }

    private void validatePutPayloads(List<EditorOperation> operations) {
        for (EditorOperation operation : operations) {
            if (operation instanceof EditorOperation.PutTile put
                    && !payloads.containsKey(put.newHash())) {
                throw new IllegalArgumentException(
                        "Missing payload for put-tile hash " + put.newHash());
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EditorEdit edit)
                || !forward.equals(edit.forward)
                || !inverse.equals(edit.inverse)
                || !payloads.keySet().equals(edit.payloads.keySet())) {
            return false;
        }
        for (Sha256 hash : payloads.keySet()) {
            if (!Arrays.equals(payloads.get(hash), edit.payloads.get(hash))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(forward, inverse);
        int payloadHash = 0;
        for (Map.Entry<Sha256, byte[]> entry : payloads.entrySet()) {
            payloadHash += 31 * entry.getKey().hashCode()
                    + Arrays.hashCode(entry.getValue());
        }
        return 31 * result + payloadHash;
    }

    @Override
    public String toString() {
        return "EditorEdit[forward=" + forward + ", inverse=" + inverse
                + ", payloadHashes=" + payloads.keySet() + "]";
    }
}
