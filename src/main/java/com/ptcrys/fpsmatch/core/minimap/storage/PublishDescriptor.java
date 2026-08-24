package com.ptcrys.fpsmatch.core.minimap.storage;

import com.google.gson.JsonObject;
import com.ptcrys.fpsmatch.core.minimap.codec.MinimapCodecs;
import com.ptcrys.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;
import java.util.regex.Pattern;

public final class PublishDescriptor {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final String publishToken;
    private final long baseRevision;
    private final long publishRevision;
    private final long expiresAtEpochMillis;
    private final Sha256 sourceHash;
    private final Sha256 runtimeHash;
    private final Sha256 runtimeContainerHash;
    private final Sha256 descriptorChecksum;

    public PublishDescriptor(
            String publishToken,
            long baseRevision,
            long publishRevision,
            long expiresAtEpochMillis,
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash
    ) {
        if (publishToken == null || !TOKEN.matcher(publishToken).matches()) {
            throw new IllegalArgumentException("Publish token is not a safe identifier");
        }
        if (baseRevision < 0 || publishRevision < 0 || publishRevision <= baseRevision) {
            throw new IllegalArgumentException("Publish revisions must increase monotonically");
        }
        if (expiresAtEpochMillis < 0) {
            throw new IllegalArgumentException("Publish expiration must be non-negative");
        }
        this.publishToken = publishToken;
        this.baseRevision = baseRevision;
        this.publishRevision = publishRevision;
        this.expiresAtEpochMillis = expiresAtEpochMillis;
        this.sourceHash = Objects.requireNonNull(sourceHash, "sourceHash");
        this.runtimeHash = Objects.requireNonNull(runtimeHash, "runtimeHash");
        this.runtimeContainerHash = Objects.requireNonNull(runtimeContainerHash, "runtimeContainerHash");
        this.descriptorChecksum = Sha256Digest.of(canonicalBytes());
    }

    public PublishDescriptor(
            String publishToken,
            long baseRevision,
            long publishRevision,
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash
    ) {
        this(
                publishToken,
                baseRevision,
                publishRevision,
                Long.MAX_VALUE,
                sourceHash,
                runtimeHash,
                runtimeContainerHash
        );
    }

    public String publishToken() {
        return publishToken;
    }

    public long baseRevision() {
        return baseRevision;
    }

    public long publishRevision() {
        return publishRevision;
    }

    public long expiresAtEpochMillis() {
        return expiresAtEpochMillis;
    }

    public Sha256 sourceHash() {
        return sourceHash;
    }

    public Sha256 runtimeHash() {
        return runtimeHash;
    }

    public Sha256 runtimeContainerHash() {
        return runtimeContainerHash;
    }

    public Sha256 descriptorChecksum() {
        return descriptorChecksum;
    }

    public byte[] canonicalBytes() {
        JsonObject object = new JsonObject();
        object.addProperty("baseRevision", Long.toString(baseRevision));
        object.addProperty("expiresAtEpochMillis", Long.toString(expiresAtEpochMillis));
        object.addProperty("publishRevision", Long.toString(publishRevision));
        object.addProperty("publishToken", publishToken);
        object.addProperty("runtimeContainerHash", runtimeContainerHash.value());
        object.addProperty("runtimeHash", runtimeHash.value());
        object.addProperty("sourceHash", sourceHash.value());
        return JcsCanonicalizer.canonicalize(object);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PublishDescriptor descriptor)) {
            return false;
        }
        return baseRevision == descriptor.baseRevision
                && publishRevision == descriptor.publishRevision
                && expiresAtEpochMillis == descriptor.expiresAtEpochMillis
                && publishToken.equals(descriptor.publishToken)
                && sourceHash.equals(descriptor.sourceHash)
                && runtimeHash.equals(descriptor.runtimeHash)
                && runtimeContainerHash.equals(descriptor.runtimeContainerHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                publishToken,
                baseRevision,
                publishRevision,
                expiresAtEpochMillis,
                sourceHash,
                runtimeHash,
                runtimeContainerHash
        );
    }
}
