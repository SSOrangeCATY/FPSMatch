package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.screen.MVPHud;
import com.phasetranscrystal.fpsmatch.core.data.MvpReason;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MvpMessageS2CPacket {
    private final MvpReason mvpReason;

    public MvpMessageS2CPacket(MvpReason mvpReason) {
        this.mvpReason = mvpReason;
    }
    public static void encode(MvpMessageS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.mvpReason.uuid);
        buf.writeComponent(packet.mvpReason.teamName);
        buf.writeComponent(packet.mvpReason.playerName);
        buf.writeComponent(packet.mvpReason.mvpReason);
        buf.writeComponent(packet.mvpReason.extraInfo1);
        buf.writeComponent(packet.mvpReason.extraInfo2);
    }

    public static MvpMessageS2CPacket decode(FriendlyByteBuf buf) {
        return new MvpMessageS2CPacket(new MvpReason.Builder(buf.readUUID())
                .setTeamName(buf.readComponent())
                .setPlayerName(buf.readComponent())
                .setMvpReason(buf.readComponent())
                .setExtraInfo1(buf.readComponent())
                .setExtraInfo2(buf.readComponent())
                .build());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(()-> MVPHud.INSTANCE.triggerAnimation(this.mvpReason));
        ctx.get().setPacketHandled(true);
    }
}
