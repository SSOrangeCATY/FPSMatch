package com.ptcrys.fpsmatch.core.minimap.wire;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public sealed interface SnapshotWireMessage extends MinimapWireMessage
        permits SnapshotWireMessage.RequestWorldSnapshot,
        SnapshotWireMessage.RequestDirtySections,
        SnapshotWireMessage.WorldSnapshotManifest,
        SnapshotWireMessage.WorldSnapshotFragment,
        SnapshotWireMessage.DirtySections {
    record RequestWorldSnapshot(
            UUID requestId,
            WireIdentity.EditorContext context,
            UUID snapshotRequestId,
            Sha256 registryDigest,
            WireSnapshot.SectionKey minimum,
            WireSnapshot.SectionKey maximum,
            boolean temporaryLoad,
            List<WireSnapshot.RequestedChannel> channels
    ) implements SnapshotWireMessage {
        public RequestWorldSnapshot {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(snapshotRequestId, "snapshotRequestId");
            Objects.requireNonNull(registryDigest, "registryDigest");
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            if (minimum.sectionX() > maximum.sectionX()
                    || minimum.sectionY() > maximum.sectionY()
                    || minimum.sectionZ() > maximum.sectionZ()) {
                throw new IllegalArgumentException(
                        "Snapshot section bounds must be ordered"
                );
            }
            Objects.requireNonNull(channels, "channels");
            channels = WireCollections.copyBoundedUnique(
                    channels,
                    MinimapHardLimits.MAX_SNAPSHOT_REQUEST_CHANNELS,
                    WireSnapshot.RequestedChannel::channelId,
                    "Snapshot requested channels"
            );
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_REQUEST_WORLD_SNAPSHOT;
        }
    }

    record RequestDirtySections(
            UUID requestId,
            WireIdentity.EditorContext context,
            long sinceWorldEpoch,
            long cursor,
            int maxResults
    ) implements SnapshotWireMessage {
        public RequestDirtySections {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            if (sinceWorldEpoch < 0 || cursor < 0) {
                throw new IllegalArgumentException(
                        "Dirty-section epoch and cursor must be non-negative"
                );
            }
            if (maxResults <= 0
                    || maxResults > MinimapHardLimits.MAX_DIRTY_SECTION_RESULTS) {
                throw new IllegalArgumentException(
                        "Dirty-section result limit must be between 1 and 4096"
                );
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.C2S_EDITOR_REQUEST_DIRTY_SECTIONS;
        }
    }

    record WorldSnapshotManifest(
            UUID requestId,
            WireIdentity.EditorContext context,
            long snapshotId,
            long worldEpoch,
            Sha256 registryDigest,
            int pageIndex,
            int pageCount,
            List<WireSnapshot.SectionDescriptor> sections
    ) implements SnapshotWireMessage {
        public WorldSnapshotManifest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(registryDigest, "registryDigest");
            if (snapshotId < 0 || worldEpoch < 0) {
                throw new IllegalArgumentException(
                        "Snapshot ID and world epoch must be non-negative"
                );
            }
            if (pageCount <= 0
                    || pageCount > MinimapHardLimits.MAX_WIRE_PAGE_COUNT
                    || pageIndex < 0
                    || pageIndex >= pageCount) {
                throw new IllegalArgumentException(
                        "Snapshot manifest page coordinates are invalid"
                );
            }
            Objects.requireNonNull(sections, "sections");
            sections = WireCollections.copyBoundedUnique(
                    sections,
                    MinimapHardLimits.MAX_SNAPSHOT_SECTIONS_PER_PAGE,
                    MinimapHardLimits.MAX_SNAPSHOT_MANIFEST_DECLARED_BYTES,
                    WireSnapshot.SectionDescriptor::declaredBytes,
                    WireSnapshot.SectionDescriptor::key,
                    "Snapshot manifest sections"
            );
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_WORLD_SNAPSHOT_MANIFEST;
        }
    }

    record WorldSnapshotFragment(
            UUID requestId,
            WireIdentity.EditorContext context,
            long snapshotId,
            WireSnapshot.SectionKey section,
            long sectionRevision,
            com.ptcrys.fpsmatch.core.minimap.model.NamespacedId channelId,
            int channelVersion,
            WireTransfer.TransferFragment transfer
    ) implements SnapshotWireMessage {
        public WorldSnapshotFragment {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(section, "section");
            Objects.requireNonNull(channelId, "channelId");
            Objects.requireNonNull(transfer, "transfer");
            if (snapshotId < 0 || sectionRevision < 0) {
                throw new IllegalArgumentException(
                        "Snapshot ID and section revision must be non-negative"
                );
            }
            if (channelVersion < 0) {
                throw new IllegalArgumentException(
                        "Snapshot channel version must be non-negative"
                );
            }
            if (transfer.totalLength()
                    > MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES) {
                throw new IllegalArgumentException(
                        "Snapshot fragment exceeds its channel byte limit"
                );
            }
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_WORLD_SNAPSHOT_FRAGMENT;
        }
    }

    record DirtySections(
            UUID requestId,
            WireIdentity.EditorContext context,
            long worldEpoch,
            long cursor,
            long nextCursor,
            boolean hasMore,
            List<WireSnapshot.DirtySection> sections
    ) implements SnapshotWireMessage {
        public DirtySections {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(context, "context");
            if (worldEpoch < 0
                    || cursor < 0
                    || nextCursor < cursor
                    || hasMore && nextCursor == cursor) {
                throw new IllegalArgumentException(
                        "Dirty-section epochs and cursors must be non-negative and ordered"
                );
            }
            Objects.requireNonNull(sections, "sections");
            sections = WireCollections.copyBoundedUnique(
                    sections,
                    MinimapHardLimits.MAX_DIRTY_SECTION_RESULTS,
                    WireSnapshot.DirtySection::section,
                    "Dirty-section page"
            );
        }

        @Override
        public MinimapOpcode opcode() {
            return MinimapOpcode.S2C_DIRTY_SECTIONS;
        }
    }
}
