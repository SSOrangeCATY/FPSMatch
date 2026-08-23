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
                    public boolean isOriginal() {
                        return true;
                    }

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
    public Binding write(Binding value) {
        binding = Objects.requireNonNull(value, "value");
        return binding;
    }

    @Override
    public Binding read() {
        return binding;
    }

    public Optional<Binding> binding() {
        return Optional.ofNullable(binding);
    }

    /**
     * True only when a published binding is present. Mounted-but-unbound maps stay inactive for runtime sync.
     */
    public boolean isPublished() {
        return binding != null;
    }

    public void clearBinding() {
        binding = null;
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