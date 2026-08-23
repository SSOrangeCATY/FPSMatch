package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.Arrays;
import java.util.Optional;

public enum MediaType {
    APPLICATION_JSON("application/json"),
    IMAGE_PNG("image/png");

    private final String value;

    MediaType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<MediaType> fromValue(String value) {
        return Arrays.stream(values()).filter(type -> type.value.equals(value)).findFirst();
    }
}
