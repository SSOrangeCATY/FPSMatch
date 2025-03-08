package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.screen.hud.MVPHud;
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
        buf.writeComponent(packet.mvpReason.getTeamName());
        buf.writeComponent(packet.mvpReason.getPlayerName());
        buf.writeComponent(packet.mvpReason.getMvpReason());
        buf.writeComponent(packet.mvpReason.getExtraInfo1());
        buf.writeComponent(packet.mvpReason.getExtraInfo2());
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
