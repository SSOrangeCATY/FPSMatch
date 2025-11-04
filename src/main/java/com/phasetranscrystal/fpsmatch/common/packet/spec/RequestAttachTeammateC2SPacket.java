package com.phasetranscrystal.fpsmatch.common.packet.spec;

import com.phasetranscrystal.fpsmatch.common.spectator.FPSMSpecManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestAttachTeammateC2SPacket {

    public RequestAttachTeammateC2SPacket() {}

    public static void encode(RequestAttachTeammateC2SPacket pkt, FriendlyByteBuf buf) { }
    public static RequestAttachTeammateC2SPacket decode(FriendlyByteBuf buf) { return new RequestAttachTeammateC2SPacket(); }

    public void handle(Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sp = ctx.getSender();
            if (sp != null && sp.isSpectator()) {
                FPSMSpecManager.requestAttachTeammate(sp);
            }
        });
        ctx.setPacketHandled(true);
    }
}