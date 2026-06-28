package com.phasetranscrystal.fpsmatch.core.playerstate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlayerStateRuntime {
    private final Map<UUID, MutableRecord> players = new LinkedHashMap<>();
    private final List<PlayerStateEvent> events = new ArrayList<>();
    private final PlayerDownPolicy downPolicy;

    public PlayerStateRuntime() {
        this(PlayerDownPolicy.unlimited());
    }

    public PlayerStateRuntime(PlayerDownPolicy downPolicy) {
        this.downPolicy = Objects.requireNonNull(downPolicy, "downPolicy");
    }

    public void addPlayer(UUID playerId) {
        players.putIfAbsent(Objects.requireNonNull(playerId, "playerId"), new MutableRecord());
    }

    public void restorePlayer(UUID playerId, PlayerStateRecord snapshot) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        MutableRecord record = new MutableRecord();
        record.state = snapshot.state();
        record.downedCount = Math.max(0, snapshot.downedCount());
        record.bleedoutTicks = Math.max(0, snapshot.bleedoutTicks());
        if (record.state != MatchPlayerState.DOWNED) {
            record.bleedoutTicks = 0;
        }
        players.put(playerId, record);
    }

    public void down(UUID playerId, int bleedoutTicks) {
        MutableRecord record = record(playerId);
        if (record.state == MatchPlayerState.ALIVE) {
            MatchPlayerState previousState = record.state;
            record.downedCount++;
            record.state = downPolicy.shouldEliminateOnDown(record.downedCount)
                    ? MatchPlayerState.ELIMINATED
                    : MatchPlayerState.DOWNED;
            record.bleedoutTicks = Math.max(0, bleedoutTicks);
            if (record.state == MatchPlayerState.ELIMINATED || record.bleedoutTicks == 0) {
                record.state = MatchPlayerState.ELIMINATED;
                record.bleedoutTicks = 0;
            }
            emit(playerId, previousState, record);
        }
    }

    public void revive(UUID playerId) {
        MutableRecord record = record(playerId);
        if (record.state == MatchPlayerState.DOWNED) {
            MatchPlayerState previousState = record.state;
            record.state = MatchPlayerState.ALIVE;
            record.bleedoutTicks = 0;
            emit(playerId, previousState, record);
        }
    }

    public void eliminate(UUID playerId) {
        MutableRecord record = record(playerId);
        MatchPlayerState previousState = record.state;
        record.state = MatchPlayerState.ELIMINATED;
        record.bleedoutTicks = 0;
        emit(playerId, previousState, record);
    }

    public void extract(UUID playerId) {
        MutableRecord record = record(playerId);
        MatchPlayerState previousState = record.state;
        record.state = MatchPlayerState.EXTRACTED;
        record.bleedoutTicks = 0;
        emit(playerId, previousState, record);
    }

    public void spectate(UUID playerId) {
        MutableRecord record = record(playerId);
        MatchPlayerState previousState = record.state;
        record.state = MatchPlayerState.SPECTATING;
        record.bleedoutTicks = 0;
        emit(playerId, previousState, record);
    }

    public void tick() {
        for (Map.Entry<UUID, MutableRecord> entry : players.entrySet()) {
            MutableRecord record = entry.getValue();
            if (record.state == MatchPlayerState.DOWNED) {
                record.bleedoutTicks--;
                if (record.bleedoutTicks <= 0) {
                    MatchPlayerState previousState = record.state;
                    record.state = MatchPlayerState.ELIMINATED;
                    record.bleedoutTicks = 0;
                    emit(entry.getKey(), previousState, record);
                }
            }
        }
    }

    public MatchPlayerState stateOf(UUID playerId) {
        return record(playerId).state;
    }

    public PlayerStateRecord recordOf(UUID playerId) {
        MutableRecord record = record(playerId);
        return new PlayerStateRecord(record.state, record.downedCount, record.bleedoutTicks);
    }

    public boolean canReceiveDamage(UUID playerId) {
        return record(playerId).state == MatchPlayerState.ALIVE || record(playerId).state == MatchPlayerState.DOWNED;
    }

    public List<PlayerStateEvent> drainEvents() {
        List<PlayerStateEvent> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    private MutableRecord record(UUID playerId) {
        MutableRecord record = players.get(Objects.requireNonNull(playerId, "playerId"));
        if (record == null) {
            throw new IllegalArgumentException("Unknown player: " + playerId);
        }
        return record;
    }

    private void emit(UUID playerId, MatchPlayerState previousState, MutableRecord record) {
        if (previousState != record.state) {
            events.add(new PlayerStateEvent(
                    playerId,
                    previousState,
                    record.state,
                    record.downedCount,
                    record.bleedoutTicks
            ));
        }
    }

    private static final class MutableRecord {
        private MatchPlayerState state = MatchPlayerState.ALIVE;
        private int downedCount;
        private int bleedoutTicks;
    }
}
