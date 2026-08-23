package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.model.MinimapDefinition;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Provenance;
import com.ptcrys.fpsmatch.core.minimap.model.SourceManifest;

import java.util.Objects;

public final class ImportService {
    public ImportService() {
    }

    public static ImportResult importSource(ImportRequest request) {
        Objects.requireNonNull(request, "request");
        SourceMap source = request.source();
        SourceManifest sourceManifest = source.manifest();
        long baseRevision = request.currentRevision();
        NamespacedId targetDocumentId = resolveDocumentId(request);
        if (request.currentDocumentId() == null && baseRevision != 0) {
            throw new ContainerValidationException(
                    "An unbound import must start at base revision zero"
            );
        }

        Provenance provenance = new Provenance(
                sourceManifest.documentId(),
                sourceManifest.binding(),
                sourceManifest.dimension(),
                sourceManifest.revision(),
                source.sourceHash()
        );
        SourceManifest importedManifest = new SourceManifest(
                sourceManifest.formatVersion(),
                targetDocumentId,
                request.targetBinding(),
                baseRevision,
                request.targetDimension(),
                java.util.Optional.of(provenance),
                sourceManifest.tileEdge(),
                java.util.List.of()
        );
        MinimapDefinition sourceDefinition = source.definition();
        MinimapDefinition importedDefinition = new MinimapDefinition(
                importedManifest,
                sourceDefinition.document(),
                sourceDefinition.regions(),
                sourceDefinition.connections(),
                sourceDefinition.styles()
        );
        SourceMapDraft original = source.toDraft();
        SourceMapDraft draft = new SourceMapDraft(
                importedDefinition, original.entries(), original.authorityExtensions()
        );
        return new ImportResult(draft, provenance, baseRevision);
    }

    private static NamespacedId resolveDocumentId(ImportRequest request) {
        if (request.mode() == ImportMode.REPLACE_CURRENT) {
            if (request.currentDocumentId() == null) {
                throw new ContainerValidationException("Replace requires a current document ID");
            }
            return request.currentDocumentId();
        }

        NamespacedId candidate = request.requestedDocumentId();
        if (candidate == null) {
            if (request.currentDocumentId() != null) {
                throw new ContainerValidationException(
                        "Save-as on a bound map requires an explicit new document ID"
                );
            }
            candidate = request.source().manifest().documentId();
        }
        if (request.currentDocumentId() != null
                && request.currentDocumentId().equals(candidate)) {
            throw new ContainerValidationException(
                    "Save-as document ID must differ from the current document ID"
            );
        }
        if (request.occupiedDocumentIds().contains(candidate)) {
            throw new ContainerValidationException(
                    "Save-as document ID is already occupied: " + candidate
            );
        }
        return candidate;
    }
}
