package com.ptcrys.fpsmatch.common.packet.mapselect;

import net.minecraft.network.FriendlyByteBuf;

import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Optional;

public record MapRoomSummary(
        String gameType,
        String mapName,
        String displayName,
        String dimension,
        String areaText,
        boolean started,
        boolean debug,
        boolean allowJoinInProgress,
        int joinedPlayers,
        int maxPlayers,
        boolean currentPlayerJoined,
        boolean currentPlayerSpectating,
        boolean currentPlayerOp,
        int readyCountdownSeconds,
        Optional<MapRoomMinimapIdentity> minimapIdentity
) {
    private static final int ID_MAX_LENGTH = 128;
    private static final int TEXT_MAX_LENGTH = 512;
    private static final int HASH_LENGTH = 64;

    public MapRoomSummary {
        minimapIdentity = java.util.Objects.requireNonNull(
                minimapIdentity, "minimapIdentity"
        );
    }

    public static void encode(MapRoomSummary summary, FriendlyByteBuf buf) {
        buf.writeUtf(summary.gameType(), ID_MAX_LENGTH);
        buf.writeUtf(summary.mapName(), ID_MAX_LENGTH);
        buf.writeUtf(summary.displayName(), TEXT_MAX_LENGTH);
        buf.writeUtf(summary.dimension(), ID_MAX_LENGTH);
        buf.writeUtf(summary.areaText(), TEXT_MAX_LENGTH);
        buf.writeBoolean(summary.started());
        buf.writeBoolean(summary.debug());
        buf.writeBoolean(summary.allowJoinInProgress());
        buf.writeInt(summary.joinedPlayers());
        buf.writeInt(summary.maxPlayers());
        buf.writeBoolean(summary.currentPlayerJoined());
        buf.writeBoolean(summary.currentPlayerSpectating());
        buf.writeBoolean(summary.currentPlayerOp());
        buf.writeInt(summary.readyCountdownSeconds());
        buf.writeBoolean(summary.minimapIdentity().isPresent());
        summary.minimapIdentity().ifPresent(identity -> {
            buf.writeUtf(identity.dimension().toString(), ID_MAX_LENGTH);
            buf.writeUtf(identity.documentId().toString(), ID_MAX_LENGTH);
            buf.writeLong(identity.revision());
            buf.writeUtf(identity.sourceHash().value(), HASH_LENGTH);
            buf.writeUtf(identity.runtimeHash().value(), HASH_LENGTH);
        });
    }

    public static MapRoomSummary decode(FriendlyByteBuf buf) {
        return new MapRoomSummary(
                buf.readUtf(ID_MAX_LENGTH),
                buf.readUtf(ID_MAX_LENGTH),
                buf.readUtf(TEXT_MAX_LENGTH),
                buf.readUtf(ID_MAX_LENGTH),
                buf.readUtf(TEXT_MAX_LENGTH),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                decodeMinimapIdentity(buf)
        );
    }

    private static Optional<MapRoomMinimapIdentity> decodeMinimapIdentity(
            FriendlyByteBuf buf
    ) {
        if (!buf.readBoolean()) {
            return Optional.empty();
        }
        return Optional.of(new MapRoomMinimapIdentity(
                NamespacedId.parse(buf.readUtf(ID_MAX_LENGTH)),
                NamespacedId.parse(buf.readUtf(ID_MAX_LENGTH)),
                buf.readLong(),
                Sha256.parse(buf.readUtf(HASH_LENGTH)),
                Sha256.parse(buf.readUtf(HASH_LENGTH))
        ));
    }

    public boolean full() {
        return maxPlayers() >= 0 && joinedPlayers() >= maxPlayers();
    }
}
