package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.List;
import java.util.Objects;

public final class WireSnapshot {
    private WireSnapshot() {
    }

    public record SectionKey(int sectionX, int sectionY, int sectionZ) {
    }

    public record RequestedChannel(NamespacedId channelId, int channelVersion) {
        public RequestedChannel {
            Objects.requireNonNull(channelId, "channelId");
            if (channelVersion < 0) {
                throw new IllegalArgumentException(
                        "Snapshot channel version must be non-negative"
                );
            }
        }
    }

    public record ChannelDescriptor(
            NamespacedId channelId,
            int channelVersion,
            long totalLength,
            Sha256 objectHash,
            int fragmentCount
    ) {
        public ChannelDescriptor {
            Objects.requireNonNull(channelId, "channelId");
            Objects.requireNonNull(objectHash, "objectHash");
            if (channelVersion < 0) {
                throw new IllegalArgumentException(
                        "Snapshot channel version must be non-negative"
                );
            }
            if (totalLength <= 0
                    || totalLength > MinimapHardLimits.MAX_SNAPSHOT_CHANNEL_BYTES) {
                throw new IllegalArgumentException(
                        "Snapshot channel length is outside its hard limit"
                );
            }
            long expectedCount = (totalLength - 1L)
                    / MinimapHardLimits.MAX_WIRE_FRAGMENT_BYTES + 1L;
            if (fragmentCount <= 0
                    || fragmentCount > MinimapHardLimits.MAX_WIRE_PAGE_COUNT
                    || fragmentCount != expectedCount) {
                throw new IllegalArgumentException(
                        "Snapshot channel fragment count is not canonical"
                );
            }
        }
    }

    public record SectionDescriptor(
            SectionKey key,
            long sectionRevision,
            boolean stale,
            List<ChannelDescriptor> channels
    ) {
        public SectionDescriptor {
            Objects.requireNonNull(key, "key");
            if (sectionRevision < 0) {
                throw new IllegalArgumentException(
                        "Snapshot section revision must be non-negative"
                );
            }
            Objects.requireNonNull(channels, "channels");
            channels = WireCollections.copyBoundedUnique(
                    channels,
                    MinimapHardLimits.MAX_SNAPSHOT_CHANNELS_PER_SECTION,
                    ChannelDescriptor::channelId,
                    "Snapshot section channels"
            );
        }

        long declaredBytes() {
            long total = 0;
            for (ChannelDescriptor channel : channels) {
                total = Math.addExact(total, channel.totalLength());
            }
            return total;
        }
    }

    public record DirtySection(SectionKey section, long sectionRevision) {
        public DirtySection {
            Objects.requireNonNull(section, "section");
            if (sectionRevision < 0) {
                throw new IllegalArgumentException(
                        "Dirty section revision must be non-negative"
                );
            }
        }
    }
}
