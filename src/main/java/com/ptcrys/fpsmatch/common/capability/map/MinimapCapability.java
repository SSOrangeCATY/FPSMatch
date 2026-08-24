package com.ptcrys.fpsmatch.common.capability.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ptcrys.fpsmatch.core.capability.FPSMCapability;
import com.ptcrys.fpsmatch.core.capability.FPSMCapabilityManager;
import com.ptcrys.fpsmatch.core.capability.map.MapCapability;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.minimap.codec.MinimapCodecs;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit map attachment for the currently published minimap references.
 * Image content remains in the minimap repository and client cache.
 * Mounting the capability does not activate runtime download until a binding is written.
 */
public final class MinimapCapability extends MapCapability
        implements FPSMCapability.Savable<MinimapCapability.Binding> {
    private Binding binding;

    private MinimapCapability(BaseMap map) {
        super(map);
    }

    public static void register() {
        FPSMCapabilityManager.register(
                FPSMCapabilityManager.CapabilityType.MAP,
                MinimapCapability.class,
                new Factory<>() {
                    @Override
                    public MinimapCapability create(BaseMap map) {
                        return new MinimapCapability(map);
                    }
                }
        );
    }

    @Override
    public Codec<Binding> codec() {
        return Binding.CODEC;
    }

    @Override
    public synchronized Binding write(Binding value) {
        binding = Objects.requireNonNull(value, "value");
        return binding;
    }

    @Override
    public synchronized Binding read() {
        return binding;
    }

    public synchronized Optional<Binding> binding() {
        return Optional.ofNullable(binding);
    }

    /**
     * True only when a published binding is present. Mounted-but-unbound maps stay inactive for runtime sync.
     */
    public synchronized boolean isPublished() {
        return binding != null;
    }

    public synchronized void clearBinding() {
        binding = null;
    }

    /**
     * Clears only the binding this caller owns. The capability monitor is the shared authority
     * across coordinator/store instances, so a later foreign binding cannot be cleared.
     */
    public synchronized BindingClearResult compareAndClearBinding(Binding expected) {
        Objects.requireNonNull(expected, "expected");
        if (binding == null) {
            return BindingClearResult.ALREADY_ABSENT;
        }
        if (!binding.equals(expected)) {
            return BindingClearResult.MISMATCH;
        }
        binding = null;
        return BindingClearResult.CLEARED;
    }

    public enum BindingClearResult {
        CLEARED,
        ALREADY_ABSENT,
        MISMATCH,
        UNAVAILABLE
    }

    public record Binding(
            NamespacedId dimension,
            NamespacedId documentId,
            long revision,
            Sha256 sourceHash,
            Sha256 runtimeHash
    ) {
        public static final Codec<Binding> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        NamespacedId.codec().fieldOf("dimension").forGetter(Binding::dimension),
                        NamespacedId.codec().fieldOf("documentId").forGetter(Binding::documentId),
                        MinimapCodecs.NON_NEGATIVE_LONG.fieldOf("revision").forGetter(Binding::revision),
                        Sha256.codec().fieldOf("sourceHash").forGetter(Binding::sourceHash),
                        Sha256.codec().fieldOf("runtimeHash").forGetter(Binding::runtimeHash)
                ).apply(instance, Binding::new)
        );

        public Binding {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(sourceHash, "sourceHash");
            Objects.requireNonNull(runtimeHash, "runtimeHash");
            if (revision < 0) {
                throw new IllegalArgumentException("Minimap revision cannot be negative");
            }
        }
    }
}
