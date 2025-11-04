package com.phasetranscrystal.fpsmatch.common.spectator;

import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.spec.RequestKillCamFallbackC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.spec.KillCamS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.spec.SpectateModeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.spec.SwitchSpectateC2SPacket;
import com.phasetranscrystal.fpsmatch.common.spectator.teammate.SpectateMode;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.MapTeams;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.phasetranscrystal.fpsmatch.util.FPSMFormatUtil.fmt2;

@Mod.EventBusSubscriber
public final class FPSMSpecManager {

    private static final Logger LOG = LogUtils.getLogger();

    private static final Map<UUID, SpectateMode> SPECTATE_MODE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SENT_NS = new ConcurrentHashMap<>();
    private static final long DEDUP_NS = 250_000_000L; // 0.25s

    public static void sendKillCamAndAttach(ServerPlayer dead, DamageSource source) {
        ServerPlayer killer = FPSMUtil.getKiller(dead,source);
        if(killer == null) return;

        Vec3 kEye = killer.getEyePosition(1.0F);
        Vec3 dEye = DamagePosTracker.consumeVictimEye(dead).orElseGet(() -> dead.getEyePosition(1.0F));

        ItemStack weapon = FPSMUtil.getKillerWeapon(source);

        sendKillCamAndAttach(dead,killer,weapon,kEye,dEye);
    }

    public static void sendKillCamAndAttach(ServerPlayer dead,
                                                 ServerPlayer killer,
                                                 ItemStack weapon,
                                                 Vec3 kEye, Vec3 dEye) {
        if (dead == null || killer == null) return;

        long now = System.nanoTime();
        Long prev = LAST_SENT_NS.get(dead.getUUID());
        if (prev != null && now - prev < DEDUP_NS) return;
        LAST_SENT_NS.put(dead.getUUID(), now);

        ItemStack weaponForSend = (weapon == null) ? ItemStack.EMPTY : weapon.copy();
        if (!weaponForSend.isEmpty() && weaponForSend.getCount() != 1) weaponForSend.setCount(1);

        LOG.info("[KillCamS] SEND packet to '{}'  killer='{}'  A(victimEye)=({},{},{})  B(killerEye)=({},{},{})  item='{}'",
                dead.getGameProfile().getName(), killer.getGameProfile().getName(),
                fmt2(dEye.x), fmt2(dEye.y), fmt2(dEye.z),
                fmt2(kEye.x), fmt2(kEye.y), fmt2(kEye.z),
                weaponForSend.isEmpty() ? "EMPTY" : weaponForSend.getHoverName().getString());

        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> dead),
                new KillCamS2CPacket(
                        killer.getUUID(), killer.getName().getString(), weaponForSend,
                        kEye.x, kEye.y, kEye.z,
                        dEye.x, dEye.y, dEye.z));

        // KillCam 播放阶段：保持 FREE（客户端黑屏完后再主动请求附身）
        SPECTATE_MODE.put(dead.getUUID(), SpectateMode.FREE);
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> dead),
                new SpectateModeS2CPacket(SpectateMode.FREE));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.side.isClient() || e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer sp)) return;

        UUID id = sp.getUUID();
        SpectateMode mode = SPECTATE_MODE.getOrDefault(id, SpectateMode.FREE);

        // 玩家不是观战：清理为 FREE
        if (!sp.isSpectator()) {
            if (mode != SpectateMode.FREE) {
                SPECTATE_MODE.remove(id);
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp),
                        new SpectateModeS2CPacket(SpectateMode.FREE));
            }
            return;
        }

        // 仅在 ATTACH 下进行续切守护
        if (mode == SpectateMode.ATTACH && !isCameraOnTeammate(sp)) {
            boolean ok = forceAttachToNearestTeammate(sp);
            if (!ok) {
                SPECTATE_MODE.put(id, SpectateMode.FREE);
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp),
                        new SpectateModeS2CPacket(SpectateMode.FREE));
            }
        }
    }

    public static void requestAttachTeammate(ServerPlayer sp){
        if (sp == null || !sp.isSpectator()) return;
        boolean ok = forceAttachToNearestTeammate(sp);
        if (ok) {
            markAttach(sp);
        } else {
            // 没有可附身队友：仍保持 FREE
        }
    }

    private static void markAttach(ServerPlayer sp){
        SPECTATE_MODE.put(sp.getUUID(), SpectateMode.ATTACH);
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp),
                new SpectateModeS2CPacket(SpectateMode.ATTACH));
    }

    private static boolean isCameraOnTeammate(ServerPlayer sp){
        Optional<BaseMap> mapOpt = FPSMCore.getInstance().getMapByPlayer(sp);
        if (mapOpt.isEmpty()) return false;
        MapTeams teams = mapOpt.get().getMapTeams();
        if (teams == null) return false;

        var myTeamOpt = teams.getTeamByPlayer(sp.getUUID());
        if (myTeamOpt.isEmpty()) return false;

        Entity cam = sp.getCamera();
        if (!(cam instanceof ServerPlayer cp)) return false;
        if (!cp.isAlive() || cp.isSpectator()) return false;

        var camTeamOpt = teams.getTeamByPlayer(cp.getUUID());
        return camTeamOpt.isPresent() && camTeamOpt.get() == myTeamOpt.get();
    }

    private static boolean forceAttachToNearestTeammate(ServerPlayer sp) {
        Optional<BaseMap> mapOpt = FPSMCore.getInstance().getMapByPlayer(sp);
        if (mapOpt.isEmpty()) return false;
        MapTeams teams = mapOpt.get().getMapTeams();
        if (teams == null) return false;

        var myTeamOpt = teams.getTeamByPlayer(sp.getUUID());
        if (myTeamOpt.isEmpty()) return false;

        ServerPlayer tgt = teams.getJoinedPlayers().stream()
                .map(pd -> pd.getPlayer().orElse(null))
                .filter(p -> p != null && p != sp && p.isAlive() && !p.isSpectator())
                .filter(p -> teams.getTeamByPlayer(p.getUUID()).orElse(null) == myTeamOpt.get())
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(sp)))
                .orElse(null);

        if (tgt != null) {
            sp.setCamera(tgt);
            return true;
        }
        return false;
    }


    @OnlyIn(Dist.CLIENT)
    public static void requestKillCamFallback(@NotNull UUID killer){
        FPSMatch.INSTANCE.sendToServer(new RequestKillCamFallbackC2SPacket(killer));
    }

    @OnlyIn(Dist.CLIENT)
    public static void sendSwitchSpectate(SwitchSpectateC2SPacket.SwitchDirection dir){
        FPSMatch.INSTANCE.sendToServer(new SwitchSpectateC2SPacket(dir));
    }
}