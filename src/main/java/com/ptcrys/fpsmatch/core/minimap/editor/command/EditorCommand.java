package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;

public record EditorCommand(long sequence, Sha256 previousRoot, EditorEdit edit) {
    public EditorCommand {
        if (sequence <= 0) {
            throw new IllegalArgumentException("Command sequence must be positive");
        }
        Objects.requireNonNull(previousRoot, "previousRoot");
        Objects.requireNonNull(edit, "edit");
    }

    public byte[] descriptorBytes() {
        return EditorCommandHasher.descriptorBytes(edit);
    }

    public Sha256 payloadHash() {
        return EditorCommandHasher.payloadHash(edit);
    }

    public Sha256 rootHash() {
        return EditorCommandHasher.nextRoot(previousRoot, sequence, payloadHash());
    }

    public Sha256 previousRootHash() {
        return previousRoot;
    }

    public Sha256 baseRootHash() {
        return previousRoot;
    }

    public Sha256 resultingRootHash() {
        return rootHash();
    }
}
