package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.CSGameOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CSGameSettingsPacket {
    private int cTWinnerRounds;
    private int tWinnerRounds;
    private int pauseTime;
    private int roundTime;
    private boolean isDebug;
    private boolean isStart;
    private boolean isError;
    private boolean isPause;
    private boolean isWaiting;
    private boolean isWarmTime;
    private boolean isWaitingWinner;

    public CSGameSettingsPacket(int cTWinnerRounds,
                                int tWinnerRounds,
                                int pauseTime,
                                int roundTime,
                                boolean isDebug,
                                boolean isStart,
                                boolean isError,
                                boolean isPause,
                                boolean isWaiting,
                                boolean isWaitingWinner) {
        this.cTWinnerRounds = cTWinnerRounds;
        this.tWinnerRounds = tWinnerRounds;
        this.pauseTime = pauseTime;
        this.roundTime = roundTime;
        this.isDebug = isDebug;
        this.isStart = isStart;
        this.isError = isError;
        this.isPause = isPause;
        this.isWaiting = isWaiting;
        this.isWaitingWinner = isWaitingWinner;
    }

    public static void encode(CSGameSettingsPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.cTWinnerRounds);
        buf.writeInt(packet.tWinnerRounds);
        buf.writeInt(packet.pauseTime);
        buf.writeInt(packet.roundTime);
        buf.writeBoolean(packet.isDebug);
        buf.writeBoolean(packet.isStart);
        buf.writeBoolean(packet.isError);
        buf.writeBoolean(packet.isPause);
        buf.writeBoolean(packet.isWaiting);
        buf.writeBoolean(packet.isWaitingWinner);
    }

    public static CSGameSettingsPacket decode(FriendlyByteBuf buf) {
        return new CSGameSettingsPacket(
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            CSGameOverlay.cTWinnerRounds = this.cTWinnerRounds;
            CSGameOverlay.tWinnerRounds = this.tWinnerRounds;
            CSGameOverlay.pauseTime = this.pauseTime;
            CSGameOverlay.roundTime = this.roundTime;
            CSGameOverlay.isDebug = this.isDebug;
            CSGameOverlay.isStart = this.isStart;
            CSGameOverlay.isError = this.isError;
            CSGameOverlay.isPause = this.isPause;
            CSGameOverlay.isWaiting = this.isWaiting;
            CSGameOverlay.isWarmTime = this.isWarmTime;
            CSGameOverlay.isWaitingWinner = this.isWaitingWinner;
        });
        ctx.get().setPacketHandled(true);
    }
}