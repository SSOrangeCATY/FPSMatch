package com.phasetranscrystal.fpsmatch.common.packet.tacz;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.compat.client.tacz.animation.GunAnimationController;
import com.phasetranscrystal.fpsmatch.compat.client.tacz.fakeitem.ClientFakeItemManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class WatchedPlayerInspectS2CPacket {
    private final UUID id;

    public WatchedPlayerInspectS2CPacket(UUID id) {
        this.id = id;
    }

    public static WatchedPlayerInspectS2CPacket decode(FriendlyByteBuf b) {
        return new WatchedPlayerInspectS2CPacket(b.readUUID());
    }

    public static void encode(WatchedPlayerInspectS2CPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.id);
    }

    public UUID getPlayerId() {
        return id;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()){
                Minecraft mc = Minecraft.getInstance();
                LocalPlayer localPlayer = mc.player;
                if (localPlayer == null) return;

                UUID targetId = this.getPlayerId();
                FPSMatch.debug("[TaczCompatClientNetwork] Received S2CWatchedPlayerInspectPacket: playerId={}", targetId);

                if (localPlayer.getUUID().equals(targetId)) {
                    FPSMatch.debug("[TaczCompatClientNetwork] I'm the one who is inspecting => play local inspect");
                    GunAnimationController.handleInspect(localPlayer);
                } else if (localPlayer.isSpectator()
                        && mc.getCameraEntity() instanceof Player cam
                        && cam.getUUID().equals(targetId)) {
                    FPSMatch.debug("[TaczCompatClientNetwork] I'm spectating that player => replicate local inspect");
                    ItemStack targetStack = cam.getMainHandItem();
                    if (!targetStack.isEmpty()) {
                        ClientFakeItemManager.equipOrUpdateForSpectator(localPlayer, targetStack.copy());
                        ClientFakeItemManager.tickUpdate(localPlayer);
                    }
                    GunAnimationController.handleInspect(localPlayer);
                } else {
                    FPSMatch.debug("[TaczCompatClientNetwork] Packet ignored: not self nor current spectate target.");
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}