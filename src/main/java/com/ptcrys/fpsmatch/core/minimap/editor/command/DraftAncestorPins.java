package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

public interface DraftAncestorPins {
    void pin(Sha256 baseSourceHash);

    void unpin(Sha256 baseSourceHash);

    boolean isPinned(Sha256 baseSourceHash);
}
