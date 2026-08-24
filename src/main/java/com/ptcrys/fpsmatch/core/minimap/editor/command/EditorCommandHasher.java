package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ptcrys.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.List;
import java.util.Objects;

public final class EditorCommandHasher {
    private EditorCommandHasher() {
    }

    public static byte[] descriptorBytes(EditorEdit edit) {
        Objects.requireNonNull(edit, "edit");
        return descriptorBytes(edit.forward());
    }

    public static byte[] descriptorBytes(List<EditorOperation> operations) {
        Objects.requireNonNull(operations, "operations");
        JsonArray encoded = new JsonArray();
        for (EditorOperation operation : operations) {
            encoded.add(encode(Objects.requireNonNull(operation, "operation")));
        }
        JsonObject descriptor = new JsonObject();
        descriptor.add("operations", encoded);
        return JcsCanonicalizer.canonicalize(descriptor);
    }

    public static Sha256 payloadHash(EditorEdit edit) {
        return Sha256Digest.of(descriptorBytes(edit));
    }

    public static Sha256 nextRoot(
            Sha256 previousRoot,
            long sequence,
            Sha256 payloadHash
    ) {
        Objects.requireNonNull(previousRoot, "previousRoot");
        Objects.requireNonNull(payloadHash, "payloadHash");
        if (sequence <= 0) {
            throw new IllegalArgumentException("Command sequence must be positive");
        }
        JsonObject value = new JsonObject();
        // Sequence is a JSON string to stay byte-identical to DraftStore.nextRoot.
        value.addProperty("opSequence", Long.toString(sequence));
        value.addProperty("payloadHash", payloadHash.value());
        value.addProperty("previousRootHash", previousRoot.value());
        return Sha256Digest.of(JcsCanonicalizer.canonicalize(value));
    }

    /** Compatibility order used by the DraftStore-facing command contract. */
    public static Sha256 nextRoot(
            long sequence,
            Sha256 payloadHash,
            Sha256 previousRoot
    ) {
        return nextRoot(previousRoot, sequence, payloadHash);
    }

    private static JsonObject encode(EditorOperation operation) {
        JsonObject value = new JsonObject();
        value.addProperty("kind", operation.kind());
        value.addProperty("path", operation.path());
        if (operation instanceof EditorOperation.SetOpacity setOpacity) {
            value.addProperty("opacity", setOpacity.opacity());
        } else if (operation instanceof EditorOperation.SetVisibility setVisibility) {
            value.addProperty("visible", setVisibility.visible());
        } else if (operation instanceof EditorOperation.SetLocked setLocked) {
            value.addProperty("locked", setLocked.locked());
        } else if (operation instanceof EditorOperation.PutTile putTile) {
            putTile.oldHash().ifPresent(hash -> value.addProperty("oldHash", hash.value()));
            value.addProperty("newHash", putTile.newHash().value());
        } else if (operation instanceof EditorOperation.DeleteTile deleteTile) {
            value.addProperty("oldHash", deleteTile.oldHash().value());
        } else {
            throw new IllegalArgumentException("Unsupported editor operation: " + operation);
        }
        return value;
    }
}
