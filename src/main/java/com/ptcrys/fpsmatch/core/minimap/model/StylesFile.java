package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.List;

public record StylesFile(List<MinimapStyle> styles) {
    public StylesFile {
        styles = List.copyOf(styles);
    }
}
