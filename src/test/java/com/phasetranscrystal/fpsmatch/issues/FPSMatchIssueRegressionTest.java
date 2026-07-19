package com.phasetranscrystal.fpsmatch.issues;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FPSMatchIssueRegressionTest {

    @Test
    void setTempDamageAssignsTheMethodParameter() throws IOException {
        String playerData = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/data/PlayerData.java"));
        String setTempDamage = playerData.substring(playerData.indexOf("public void setTempDamage"), playerData.indexOf("public void addScore"));

        assertTrue(setTempDamage.contains("this._damage = tempDamage;"));
        assertFalse(setTempDamage.contains("this._damage = damage;"));
    }

    @Test
    void clientTeamLookupDoesNotReportEveryCachedPlayerAsCsdm() throws IOException {
        String clientData = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/data/FPSMClientGlobalData.java"));

        assertFalse(clientData.contains("new PlayerTeamData(\"csdm\", cached)"));
        assertFalse(clientData.contains("playerDataCache.put(uuid, data);"));
        assertTrue(clientData.contains("new PlayerTeamData(teamName, data)"));
    }

    @Test
    void addTeamPacketDoesNotIgnoreSameNameFromDifferentMap() throws IOException {
        String handlers = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/net/FPSMClientPacketHandlers.java"));

        assertFalse(handlers.contains("if (data.getTeamByName(packet.teamData().name()).isPresent()) return;"));
        assertTrue(handlers.contains("existingTeamMatchesPacket"));
        assertTrue(handlers.contains("team.gameType.equals(packet.gameType()) && team.mapName.equals(packet.mapName())"));
        assertTrue(handlers.contains("if (existingTeam.filter(team -> existingTeamMatchesPacket(team, packet)).isPresent()) return;"));
    }

    @Test
    void asyncPersistenceUsesSharedExecutorInsteadOfPerCallThreadPools() throws IOException {
        String dataManager = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/persistence/FPSMDataManager.java"));

        assertFalse(dataManager.contains("Executors.newSingleThreadExecutor()"));
        assertTrue(dataManager.contains("ASYNC_EXECUTOR"));
    }

    @Test
    void shopEditorSlotsUseCenteredGridOffsets() throws IOException {
        String container = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/EditorShopContainer.java"));
        String screen = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/EditorShopScreen.java"));

        assertFalse(container.contains("int start = 5;"));
        assertTrue(container.contains("getGridLeft() + col * SLOT_SPACING_X"));
        assertTrue(container.contains("getGridTop() + row * SLOT_SPACING_Y"));
        assertFalse(screen.contains("this.leftPos = 0;"));
        assertFalse(screen.contains("this.topPos = 0;"));
        assertTrue(screen.contains("this.leftPos = (this.width - this.imageWidth) / 2;"));
        assertTrue(screen.contains("this.topPos = Math.max(0, (this.height - this.imageHeight) / 2);"));
        assertTrue(screen.contains("leftPos + imageWidth / 2 - FPSMGuiTheme.BUTTON_LARGE_WIDTH / 2, topPos + imageHeight - 30"));
    }

    @Test
    void respawnEventIsRegisteredAndRestoresMapPlayerState() throws IOException {
        String eventHook = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/event/FPSMEventHook.java"))
                .replace("\r\n", "\n");
        String baseRoundMap = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/map/BaseRoundMap.java"))
                .replace("\r\n", "\n");
        String respawnHandler = eventHook.substring(eventHook.indexOf("onPlayerRespawnEvent"));

        assertTrue(eventHook.contains("@SubscribeEvent(priority = EventPriority.LOWEST)\n    public static void onPlayerRespawnEvent"));
        assertTrue(respawnHandler.contains("map instanceof BaseRoundMap<?, ?> roundMap"));
        assertTrue(respawnHandler.contains("roundMap.handleRespawn(player)"));
        assertTrue(baseRoundMap.contains("public void handleRespawn(ServerPlayer player)"));
        String baseRespawn = baseRoundMap.substring(baseRoundMap.indexOf("public void handleRespawn"), baseRoundMap.indexOf("/**\n     * 重新创建 lifecycle"));
        assertTrue(baseRespawn.contains("data.setLiving(true)"));
        assertTrue(baseRespawn.contains("teleportPlayerToReSpawnPoint(player)"));
    }

    @Test
    void suicideGunKillCannotKeepHeadshotFlag() throws IOException {
        String eventHook = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/event/FPSMDeathPipelineEventHook.java"));
        String applyGunKillDetail = eventHook.substring(eventHook.indexOf("private static void applyGunKillDetail"));

        assertTrue(applyGunKillDetail.contains("boolean selfKill"));
        assertTrue(applyGunKillDetail.contains("context.setHeadShot((context.isHeadShot() || gunKill.isHeadShot()) && !selfKill);"));
    }

    @Test
    void headshotKillsUseRoundTemporaryStorageWhenRoundsAreEnabled() throws IOException {
        String playerData = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/data/PlayerData.java"));
        String addHeadshotKill = playerData.substring(playerData.indexOf("public void addHeadshotKill"), playerData.indexOf("public void setHeadshotKills"));
        String saveRoundData = playerData.substring(playerData.indexOf("public void saveRoundData"), playerData.indexOf("public void reset()"));

        assertTrue(playerData.contains("private int _headshotKills"));
        assertTrue(playerData.contains("return headshotKills + (enableRounds ? _headshotKills : 0);"));
        assertTrue(playerData.contains("public int getTempHeadshotKills()"));
        assertTrue(addHeadshotKill.contains("_headshotKills++"));
        assertTrue(saveRoundData.contains("this.headshotKills += _headshotKills;"));
        assertTrue(saveRoundData.contains("this._headshotKills = 0;"));
    }

    @Test
    void suicideDeathContextCannotRemainHeadshot() throws IOException {
        String deathContext = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/map/DeathContext.java"));

        assertTrue(deathContext.contains("public boolean isSuicide()"));
        assertTrue(deathContext.contains("this.headShot = headShot && !isSuicide();"));
        assertTrue(deathContext.contains("if (isSuicide()) {"));
        assertTrue(deathContext.contains("this.headShot = false;"));
    }

    @Test
    void taczHeadshotDamageFallbackSurvivesDelayedDeathPipeline() throws IOException {
        String eventHook = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/event/FPSMDeathPipelineEventHook.java"));

        assertTrue(eventHook.contains("private static final long RECENT_GUN_HIT_MATCH_WINDOW_TICKS = 5L;"));
        assertTrue(eventHook.contains("private static final long RECENT_GUN_HIT_RETENTION_TICKS = 20L;"));
        assertTrue(eventHook.contains("private static final Map<UUID, RecentGunHitDetail> recentGunHits"));
        assertTrue(eventHook.contains("public static void onGunDamage(FPSMGunDamageEvent event)"));
        assertTrue(eventHook.contains("recentGunHits.put(hurt.getUUID(), new RecentGunHitDetail("));
        assertTrue(eventHook.contains("applyRecentGunHitDetail(context, recentGunHits.get(player.getUUID()))"));
        assertTrue(eventHook.contains("resolveGunKillHeadShot(deadPlayer, event.isHeadShot(), attacker)"));
        assertTrue(eventHook.contains("context.setHeadShot((context.isHeadShot() || gunKill.isHeadShot()) && !selfKill);"));
        assertTrue(eventHook.contains("RecentGunHitDetail recentGunHit = recentGunHits.remove(player.getUUID())"));
        assertTrue(eventHook.contains("context.setAttacker(recentGunHit.attacker());"));
        assertTrue(eventHook.contains("if (attacker != null && !attacker.getUUID().equals(recentGunHit.attacker().getUUID()))"));
        assertTrue(eventHook.contains("return isRecentGunHitFresh(context.getCreatedTick(), recentGunHit)"));
        assertTrue(eventHook.contains("isRecentGunHitFresh(deadPlayer.serverLevel().getGameTime(), recentGunHit)"));
        assertTrue(eventHook.contains("purgeExpiredRecentGunHits(now);"));
        assertTrue(eventHook.contains("private static void purgeExpiredRecentGunHits(long currentTick)"));
        assertTrue(eventHook.contains("private record RecentGunHitDetail(boolean isHeadShot, ServerPlayer attacker, long createdTick,"));
        assertTrue(eventHook.contains("@Nullable Entity bullet, boolean passWall, boolean passSmoke,"));
        assertTrue(eventHook.contains("boolean scopedKill)"));
        assertFalse(eventHook.contains("recentGunHits.clear();"));
    }

    @Test
    void taczHudTextureLookupAvoidsDistExecutorSafeReferentCrash() throws IOException {
        String provider = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/compat/tacz/TACZGunProvider.java"));

        assertFalse(provider.contains("DistExecutor.safeCallWhenOn(Dist.CLIENT"));
        assertTrue(provider.contains("FMLEnvironment.dist != Dist.CLIENT"));
        assertTrue(provider.contains("GunSpecUtils.getGunHUDTexture(stack)"));
    }

    @Test
    void persistenceRootsFollowTheActiveGameDirectory() throws IOException {
        String dataManager = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/persistence/FPSMDataManager.java"));
        String configManager = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/persistence/ConfigManager.java"));

        assertTrue(dataManager.contains("FMLLoader.getGamePath()"));
        assertTrue(configManager.contains("FMLLoader.getGamePath()"));
        assertFalse(dataManager.toLowerCase().contains("curseforge"));
        assertFalse(configManager.toLowerCase().contains("curseforge"));
    }

    @Test
    void teamManagementSupportsActionsDragAndSpectatorTeams() throws IOException {
        String screen = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/FPSMTeamManageScreen.java"));
        String action = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/FPSMTeamActionScreen.java"));
        String drag = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/team/TeamDragState.java"));

        assertTrue(screen.contains("FPSMTeamActionScreen"));
        assertTrue(screen.contains("TeamDragState"));
        assertTrue(screen.contains("spectator"));
        assertTrue(action.contains("gui.fpsm.team_manage"));
        assertTrue(drag.contains("record"));
    }

    @Test
    void watchedRoomSignatureTracksInviteTargets() throws IOException {
        String sync = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/mapselect/MapRoomSyncManager.java"));

        assertTrue(sync.contains("MapRoomQueryService.computeInviteTargetSignature(map)"));
        assertTrue(sync.contains("sig = mix(sig, MapRoomQueryService.computeInviteTargetSignature(map));"));
    }

    @Test
    void shopSlotEditorUsesLdlib2PanelsWhileKeepingContainerScreenChain() throws IOException {
        String screen = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/EditShopSlotScreen.java"));
        String ui = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/client/screen/shop/ldlib2/Ldlib2EditShopSlotUi.java"));

        assertTrue(screen.contains("extends AbstractContainerScreen"));
        assertTrue(screen.contains("Ldlib2EditShopSlotUi"));
        assertTrue(screen.contains("ModularMenuUiSupport"));
        assertTrue(ui.contains("container.inventory"));
        assertTrue(ui.contains("gui.fpsm.shop_editor") || ui.contains("price") || ui.contains("Price"));
    }

    @Test
    void clientSoundRequestsStayInsideTheCurrentMapAndAreRateLimited() throws IOException {
        String packet = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/packet/FPSMSoundPlayC2SPacket.java"));

        assertTrue(packet.contains("SoundRequestPolicy.allow(player.getUUID(), location, now)"));
        assertTrue(packet.contains("FPSMCore.getInstance().getMapByPlayer(player).ifPresent(map ->"));
        assertFalse(packet.contains("ifPresentOrElse"));
        assertFalse(packet.contains("server.getPlayerList().getPlayers()"));
    }

    @Test
    void throwableRequestsRejectInvalidOrdinalsAndRequireItemApproval() throws IOException {
        String packet = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/packet/entity/ThrowEntityC2SPacket.java"));
        String contract = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/item/IThrowEntityAble.java"));

        assertTrue(packet.contains("ThrowType.fromNetworkOrdinal(buf.readVarInt())"));
        assertTrue(packet.contains("throwEntityAble.isThrowTypeAllowed(type)"));
        assertFalse(packet.contains("ThrowType.values()[buf.readInt()]"));
        assertTrue(contract.contains("default boolean isThrowTypeAllowed"));
    }

}
