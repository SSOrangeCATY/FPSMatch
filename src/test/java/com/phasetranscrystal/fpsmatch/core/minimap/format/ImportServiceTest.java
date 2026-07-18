package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportServiceTest {
    @Test
    void importUsesTargetBindingAndRevisionWhileRecordingDirectSourceProvenance() throws Exception {
        MinimapDefinition sourceDefinition = MinimapContainerFixtures.sourceDefinition();
        byte[] sourceBytes = SourceMapWriter.write(sourceDefinition);
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            MapKey target = new MapKey("fpsmatch:target", "Arena B");
            NamespacedId targetDimension = NamespacedId.parse("minecraft:the_nether");
            ImportResult result = ImportService.importSource(new ImportRequest(
                    source,
                    target,
                    targetDimension,
                    ImportMode.REPLACE_CURRENT,
                    NamespacedId.parse("fpsmatch:target-doc"),
                    12,
                    null,
                    Set.of()
            ));

            assertEquals(target, result.draft().definition().manifest().binding());
            assertEquals(targetDimension, result.draft().definition().manifest().dimension());
            assertEquals(NamespacedId.parse("fpsmatch:target-doc"),
                    result.draft().definition().manifest().documentId());
            assertEquals(12, result.baseRevision());
            assertEquals(source.manifest().revision(),
                    result.provenance().originRevision());
            assertEquals(source.sourceHash(), result.provenance().originSourceHash());
            assertEquals(source.manifest().binding(), result.provenance().originBinding());
            assertEquals(source.manifest().dimension(), result.provenance().originDimension());
            assertEquals(source.manifest().documentId(), result.provenance().originDocumentId());
            assertEquals(7, source.manifest().revision());
        }
    }

    @Test
    void saveAsRequiresAnUnoccupiedExplicitDocumentIdWhenTheTargetIsBound() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(MinimapContainerFixtures.sourceDefinition());
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            ImportRequest request = new ImportRequest(
                    source,
                    new MapKey("fpsmatch:target", "Arena B"),
                    NamespacedId.parse("minecraft:overworld"),
                    ImportMode.SAVE_AS,
                    NamespacedId.parse("fpsmatch:current"),
                    4,
                    null,
                    Set.of(NamespacedId.parse("fpsmatch:occupied"))
            );
            assertThrows(ContainerValidationException.class,
                    () -> ImportService.importSource(request));

            ImportRequest explicit = new ImportRequest(
                    source,
                    request.targetBinding(), request.targetDimension(), request.mode(),
                    request.currentDocumentId(), request.currentRevision(),
                    NamespacedId.parse("fpsmatch:new-id"),
                    Set.of(NamespacedId.parse("fpsmatch:occupied"))
            );
            ImportResult result = ImportService.importSource(explicit);
            assertEquals(NamespacedId.parse("fpsmatch:new-id"),
                    result.draft().definition().manifest().documentId());
            assertEquals(4, result.baseRevision());

            ImportRequest sameId = new ImportRequest(
                    source,
                    request.targetBinding(), request.targetDimension(), request.mode(),
                    request.currentDocumentId(), request.currentRevision(),
                    request.currentDocumentId(), Set.of()
            );
            assertThrows(ContainerValidationException.class,
                    () -> ImportService.importSource(sameId));
        }
    }
}
