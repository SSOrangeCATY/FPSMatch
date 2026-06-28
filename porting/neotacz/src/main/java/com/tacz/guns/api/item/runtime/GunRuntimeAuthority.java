package com.tacz.guns.api.item.runtime;

import java.util.Set;

public record GunRuntimeAuthority(String authorityId, Set<String> protectedFields) {
    public GunRuntimeAuthority {
        authorityId = authorityId == null ? "" : authorityId;
        protectedFields = protectedFields == null ? Set.of() : Set.copyOf(protectedFields);
    }

    public boolean canMutate(String field, String requester) {
        if (!protectedFields.contains(field)) {
            return true;
        }
        return !authorityId.isBlank() && authorityId.equals(requester);
    }
}
