package com.phasetranscrystal.fpsmatch.common.packet.spec;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.team.MapTeams;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class SwitchSpectateC2SPacket {

    public enum SwitchDirection { PREV, NEXT }

    private final SwitchDirection direction;

    public SwitchSpectateC2SPacket(SwitchDirection direction) {
        this.direction = direction;
    }

    public static void encode(SwitchSpectateC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.direction);
    }

    public static SwitchSpectateC2SPacket decode(FriendlyByteBuf buf) {
        return new SwitchSpectateC2SPacket(buf.readEnum(SwitchDirection.class));
    }

    public static void handle(SwitchSpectateC2SPacket msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sp = ctx.getSender();
            if (sp == null || !sp.isSpectator()) return;

            Optional<BaseMap> mapOpt = FPSMCore.getInstance().getMapByPlayer(sp);
            if (mapOpt.isEmpty()) return;
            BaseMap baseMap = mapOpt.get();

            MapTeams mapTeams = baseMap.getMapTeams();
            if (mapTeams == null) return;

            BaseTeam team = mapTeams.getTeamByPlayer(sp.getUUID()).orElse(null);
            if (team == null) return;

            List<ServerPlayer> teammates = team.getPlayerList().stream()
                    .map(uuid -> sp.server.getPlayerList().getPlayer(uuid))
                    .filter(p -> p != null && p.isAlive() && !p.isSpectator())
                    .toList();

            if (teammates.isEmpty()) return;

            int currentIndex = -1;
            var camera = sp.getCamera();
            for (int i = 0; i < teammates.size(); i++) {
                if (teammates.get(i) == camera) {
                    currentIndex = i;
                    break;
                }
            }
            if (currentIndex < 0) currentIndex = 0;

            int newIndex = (msg.direction == SwitchDirection.NEXT)
                    ? (currentIndex + 1) % teammates.size()
                    : (currentIndex - 1 + teammates.size()) % teammates.size();

            ServerPlayer newCamera = teammates.get(newIndex);
            if (newCamera != null && newCamera != sp) {
                sp.setCamera(newCamera);
            }
        });
        ctx.setPacketHandled(true);
    }
}