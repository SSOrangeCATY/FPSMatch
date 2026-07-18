package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.phasetranscrystal.fpsmatch.core.minimap.format.StrictJsonParser;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishStateTest {
    private static final PublishTarget TARGET = new PublishTarget(
            new com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey(
                    "fpsmatch:test", "Test Map"
            ),
            com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                    "minecraft:overworld"
            ),
            com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                    "fpsmatch:test-map"
            )
    );
    @Test
    void descriptorChecksumIsImmutableAndStateTransitionsAreStrict() {
        PublishDescriptor descriptor = new PublishDescriptor(
                "token-1",
                3,
                4,
                Sha256.parse("1".repeat(64)),
                Sha256.parse("2".repeat(64)),
                Sha256.parse("3".repeat(64))
        );
        PublishRecord reserved = PublishRecord.reserved(TARGET, descriptor);

        assertEquals(descriptor.descriptorChecksum(), reserved.descriptorChecksum());
        PublishRecord prepared = reserved.transition(PublishState.PREPARED, "validated");
        PublishRecord committed = prepared.transition(PublishState.COMMITTED, "published");
        assertEquals(PublishState.COMMITTED, committed.state());
        assertEquals(descriptor.descriptorChecksum(), committed.descriptorChecksum());
        assertNotEquals(reserved.state(), committed.state());

        assertThrows(IllegalStateException.class,
                () -> reserved.transition(PublishState.COMMITTED, "skip"));
        assertThrows(IllegalStateException.class,
                () -> committed.transition(PublishState.PREPARED, "rollback"));
    }

    @Test
    void recordRejectsUnknownDescriptorFields() {
        PublishDescriptor descriptor = new PublishDescriptor(
                "token-1", 3, 4,
                Sha256.parse("1".repeat(64)),
                Sha256.parse("2".repeat(64)),
                Sha256.parse("3".repeat(64))
        );
        JsonObject root = StrictJsonParser.parse(
                PublishRecord.reserved(TARGET, descriptor).canonicalBytes()
        ).getAsJsonObject();
        root.getAsJsonObject("descriptor").addProperty("futureField", true);

        assertThrows(
                ContainerStorageException.class,
                () -> PublishRecord.read(JcsCanonicalizer.canonicalize(root))
        );
    }

    @Test
    void validationModeIsPersistedAndLegacyRecordsDefaultToFull() {
        PublishDescriptor descriptor = descriptor();
        PublishRecord full = PublishRecord.reserved(TARGET, descriptor)
                .transition(PublishState.PREPARED, "validated");
        PublishRecord trusted = PublishRecord.trustedPrepared(
                TARGET, descriptor, "recovery rebind"
        );

        assertEquals(PairValidation.FULL, full.pairValidation());
        assertEquals(PairValidation.METADATA_TRUSTED, trusted.pairValidation());
        assertEquals(trusted, PublishRecord.read(trusted.canonicalBytes()));
        assertTrue(StrictJsonParser.parse(trusted.canonicalBytes()).getAsJsonObject()
                .has("pairValidation"));

        JsonObject legacy = StrictJsonParser.parse(full.canonicalBytes()).getAsJsonObject();
        legacy.remove("pairValidation");
        assertEquals(
                PairValidation.FULL,
                PublishRecord.read(JcsCanonicalizer.canonicalize(legacy)).pairValidation()
        );
    }

    @Test
    void validationModeRejectsUnknownValuesAndTrustedReservedRecords() {
        JsonObject root = StrictJsonParser.parse(
                PublishRecord.reserved(TARGET, descriptor()).canonicalBytes()
        ).getAsJsonObject();
        root.addProperty("pairValidation", "FUTURE_MODE");
        assertThrows(
                ContainerStorageException.class,
                () -> PublishRecord.read(JcsCanonicalizer.canonicalize(root))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PublishRecord(
                        TARGET, descriptor(), PublishState.RESERVED, "invalid",
                        PairValidation.METADATA_TRUSTED
                )
        );
    }

    @Test
    void publishTargetIsCanonicalAndRequiredAcrossStateTransitions() {
        PublishRecord reserved = PublishRecord.reserved(TARGET, descriptor());
        assertEquals(TARGET, PublishRecord.read(reserved.canonicalBytes()).target());
        assertEquals(
                TARGET,
                PublishRecord.read(
                        reserved.transition(PublishState.PREPARED, "validated")
                                .canonicalBytes()
                ).target()
        );

        JsonObject missing = StrictJsonParser.parse(
                reserved.canonicalBytes()
        ).getAsJsonObject();
        missing.remove("target");
        assertThrows(
                ContainerStorageException.class,
                () -> PublishRecord.read(JcsCanonicalizer.canonicalize(missing))
        );
    }

    private static PublishDescriptor descriptor() {
        return new PublishDescriptor(
                "token-1", 3, 4,
                Sha256.parse("1".repeat(64)),
                Sha256.parse("2".repeat(64)),
                Sha256.parse("3".repeat(64))
        );
    }
}
