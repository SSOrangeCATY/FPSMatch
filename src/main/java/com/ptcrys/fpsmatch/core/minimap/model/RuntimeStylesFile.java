package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.List;

public record RuntimeStylesFile(List<RuntimeStyle> styles) {
    public RuntimeStylesFile {
        styles = List.copyOf(styles);
    }
}
