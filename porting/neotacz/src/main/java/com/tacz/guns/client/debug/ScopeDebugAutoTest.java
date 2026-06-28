package com.tacz.guns.client.debug;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.util.MinecraftGuiCompat;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(value = Dist.CLIENT)
public final class ScopeDebugAutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("tacz.debug.scope.autotest")
            && Boolean.getBoolean("tacz.debug.scopeRender")
            && Boolean.getBoolean("mcagent.testMode");
    private static final Identifier M4A1 = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "m4a1");
    private static final Identifier AK47 = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ak47");
    private static final Identifier SCOPE_STANDARD_8X = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_standard_8x");
    private static final Identifier SCOPE_LPVO_1_6 = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_lpvo_1_6");
    private static final Identifier SCOPE_ELCAN_4X = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_elcan_4x");
    private static final Identifier SCOPE_HAMR = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_hamr");
    private static final Identifier SCOPE_ACOG_TA31 = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_acog_ta31");
    private static final ScopeCase[] TEST_CASES = buildTestCases();
    private static final Vec3 TEST_POSITION = new Vec3(72.5, 120.0, -87.5);
    private static final float TEST_YAW = floatProperty("tacz.debug.scope.yaw", 180.0F);
    private static final float TEST_PITCH = floatProperty("tacz.debug.scope.pitch", -7.0F);
    private static final int SETUP_WAIT_TICKS = 60;
    private static final int NON_ADS_TICKS = 30;
    private static final int FULL_ADS_TICKS = 30;
    private static final int MAX_TRANSITION_TICKS = 100;
    private static final String HOLD_PHASE = System.getProperty("tacz.debug.scope.holdPhase", "").trim();
    private static final int SCOPE_ZOOM_NUMBER = Math.max(0, Integer.getInteger("tacz.debug.scope.zoom", 0));

    private static int ticks;
    private static int caseIndex;
    private static int phaseTicks;
    private static int waitTicks;
    private static Phase phase = Phase.SETUP;
    private static boolean finished;
    private static boolean environmentPrepared;

    private ScopeDebugAutoTest() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.isSpectator()) {
            return;
        }
        ticks++;
        Screen screen = MinecraftGuiCompat.screen();
        if (screen instanceof DeathScreen) {
            player.respawn();
            clearScreen(minecraft);
            return;
        }
        if (screen != null) {
            if (ticks > 80) {
                clearScreen(minecraft);
            } else {
                return;
            }
        }
        ScopeCase testCase = TEST_CASES[caseIndex];
        Identifier gunId = testCase.gunId();
        Identifier scopeId = testCase.scopeId();
        if (TimelessAPI.getCommonGunIndex(gunId).isEmpty()
                || TimelessAPI.getClientGunIndex(gunId).isEmpty()
                || TimelessAPI.getClientAttachmentIndex(scopeId).isEmpty()) {
            if (ticks % 40 == 0) {
                ScopeRenderDebug.autoTest("waiting_index", scopeId, aimingProgress(player), player.getMainHandItem());
            }
            return;
        }
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        applyStableEnvironment(minecraft, player);
        applyStablePose(minecraft, player);
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        switch (phase) {
            case SETUP -> {
                setupPlayer(minecraft, player, buildGun(gunId, scopeId), gunId, scopeId);
                operator.aim(false);
                forceClientAimingProgress(operator, 0.0F);
                waitTicks = SETUP_WAIT_TICKS;
                phase = Phase.NON_ADS;
                phaseTicks = 0;
                ScopeRenderDebug.autoTest("setup:" + gunId, scopeId, aimingProgress(player), player.getMainHandItem());
            }
            case NON_ADS -> {
                ensureTestGun(minecraft, player, gunId, scopeId);
                prepareClientScopeState(operator);
                forceLocalSyncedReady(player);
                operator.aim(false);
                forceClientAimingProgress(operator, 0.0F);
                if (waitTicks > 0) {
                    waitTicks--;
                    logEvery("setup_wait", scopeId, player);
                    return;
                }
                logEvery("non_ads", scopeId, player);
                if (isHolding(Phase.NON_ADS)) {
                    return;
                }
                if (++phaseTicks >= NON_ADS_TICKS) {
                    operator.aim(true);
                    phase = Phase.ADS_TRANSITION;
                    phaseTicks = 0;
                }
            }
            case ADS_TRANSITION -> {
                ensureTestGun(minecraft, player, gunId, scopeId);
                prepareClientScopeState(operator);
                forceLocalSyncedReady(player);
                operator.aim(true);
                forceClientAimingProgress(operator, Math.min(1.0F, (phaseTicks + 1) / 30.0F));
                logEvery("ads_transition", scopeId, player);
                if (isHolding(Phase.ADS_TRANSITION)) {
                    phaseTicks++;
                    return;
                }
                float progress = aimingProgress(player);
                if (++phaseTicks >= MAX_TRANSITION_TICKS || progress >= 0.98F) {
                    phase = Phase.FULL_ADS;
                    phaseTicks = 0;
                    ScopeRenderDebug.autoTest("full_ads_enter", scopeId, progress, player.getMainHandItem());
                }
            }
            case FULL_ADS -> {
                ensureTestGun(minecraft, player, gunId, scopeId);
                prepareClientScopeState(operator);
                forceLocalSyncedReady(player);
                operator.aim(true);
                forceClientAimingProgress(operator, 1.0F);
                logEvery("full_ads", scopeId, player);
                if (isHolding(Phase.FULL_ADS)) {
                    phaseTicks++;
                    return;
                }
                if (++phaseTicks >= FULL_ADS_TICKS) {
                    operator.aim(false);
                    phase = Phase.ADS_EXIT;
                    phaseTicks = 0;
                }
            }
            case ADS_EXIT -> {
                ensureTestGun(minecraft, player, gunId, scopeId);
                prepareClientScopeState(operator);
                forceLocalSyncedReady(player);
                operator.aim(false);
                forceClientAimingProgress(operator, Math.max(0.0F, 1.0F - (phaseTicks + 1) / 30.0F));
                logEvery("ads_exit", scopeId, player);
                if (isHolding(Phase.ADS_EXIT)) {
                    phaseTicks++;
                    return;
                }
                float progress = aimingProgress(player);
                if (++phaseTicks >= MAX_TRANSITION_TICKS || progress <= 0.02F) {
                    advance(scopeId, player);
                }
            }
        }
    }

    private static ScopeCase[] buildTestCases() {
        String configured = System.getProperty("tacz.debug.scope.case", "").trim();
        if (!configured.isEmpty()) {
            List<ScopeCase> cases = new ArrayList<>();
            for (String entry : configured.split(";")) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] pieces = trimmed.split(",");
                if (pieces.length != 2) {
                    GunMod.LOGGER.warn("Ignoring invalid TACZ scope debug case '{}'; expected namespace:gun,namespace:scope", trimmed);
                    continue;
                }
                Identifier gunId = parseIdentifier(pieces[0].trim());
                Identifier scopeId = parseIdentifier(pieces[1].trim());
                if (gunId == null || scopeId == null) {
                    GunMod.LOGGER.warn("Ignoring invalid TACZ scope debug case '{}'; bad identifier", trimmed);
                    continue;
                }
                cases.add(new ScopeCase(gunId, scopeId));
            }
            if (!cases.isEmpty()) {
                return cases.toArray(ScopeCase[]::new);
            }
            GunMod.LOGGER.warn("No valid TACZ scope debug cases parsed from '{}'; falling back to default matrix", configured);
        }
        return new ScopeCase[]{
                new ScopeCase(M4A1, SCOPE_ELCAN_4X),
                new ScopeCase(M4A1, SCOPE_HAMR),
                new ScopeCase(M4A1, SCOPE_ACOG_TA31),
                new ScopeCase(AK47, SCOPE_STANDARD_8X),
                new ScopeCase(AK47, SCOPE_LPVO_1_6),
                new ScopeCase(AK47, SCOPE_ELCAN_4X),
                new ScopeCase(AK47, SCOPE_HAMR),
                new ScopeCase(AK47, SCOPE_ACOG_TA31)
        };
    }

    private static Identifier parseIdentifier(String value) {
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            return null;
        }
        try {
            return Identifier.fromNamespaceAndPath(value.substring(0, separator), value.substring(separator + 1));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static float floatProperty(String key, float fallback) {
        String configured = System.getProperty(key, "").trim();
        if (configured.isEmpty()) {
            return fallback;
        }
        try {
            return Float.parseFloat(configured);
        } catch (NumberFormatException exception) {
            GunMod.LOGGER.warn("Ignoring invalid TACZ scope debug float property {}='{}'", key, configured);
            return fallback;
        }
    }

    private static ItemStack buildGun(Identifier gunId, Identifier scopeId) {
        FireMode fireMode = TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getGunData().getFireModeSet().contains(FireMode.SEMI)
                        ? FireMode.SEMI
                        : index.getGunData().getFireModeSet().getFirst())
                .orElse(FireMode.SEMI);
        ItemStack stack = GunItemBuilder.create()
                .setId(gunId)
                .setFireMode(fireMode)
                .setAmmoCount(120)
                .setAmmoInBarrel(true)
                .putAttachment(AttachmentType.SCOPE, scopeId)
                .build();
        if (stack.isEmpty()) {
            stack = GunItemBuilder.create()
                    .setId(gunId)
                    .setFireMode(fireMode)
                    .setAmmoCount(120)
                    .setAmmoInBarrel(true)
                    .putAttachment(AttachmentType.SCOPE, scopeId)
                    .forceBuild();
        }
        applyScopeZoom(stack, scopeId);
        return stack;
    }

    private static void applyScopeZoom(ItemStack gunStack, Identifier scopeId) {
        if (SCOPE_ZOOM_NUMBER <= 0) {
            return;
        }
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            return;
        }
        ItemStack scopeStack = AttachmentItemBuilder.create().setId(scopeId).build();
        IAttachment attachment = IAttachment.getIAttachmentOrNull(scopeStack);
        if (attachment == null) {
            return;
        }
        attachment.setZoomNumber(scopeStack, SCOPE_ZOOM_NUMBER);
        gun.installAttachment(gunStack, scopeStack);
    }

    private static void setupPlayer(Minecraft minecraft, LocalPlayer player, ItemStack gunStack,
                                    Identifier gunId, Identifier scopeId) {
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, gunStack.copy());
        applyStablePose(minecraft, player);
        syncServerInventoryAndDraw(minecraft, player.getUUID(), gunStack.copy());
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        operator.getDataHolder().reset();
        prepareClientScopeState(operator);
        forceLocalSyncedReady(player);
        operator.draw(ItemStack.EMPTY);
        ScopeRenderDebug.autoTest("setup_item:" + gunId + ":" + scopeId, scopeId, aimingProgress(player), player.getMainHandItem());
    }

    private static void applyStableEnvironment(Minecraft minecraft, LocalPlayer player) {
        minecraft.options.bobView().set(false);
        if (environmentPrepared) {
            return;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
                server.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
                server.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
            });
        }
        environmentPrepared = true;
        ScopeRenderDebug.autoTest("environment_prepared", null, aimingProgress(player), player.getMainHandItem());
    }

    private static void ensureTestGun(Minecraft minecraft, LocalPlayer player, Identifier gunId, Identifier scopeId) {
        ItemStack mainHandItem = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(mainHandItem);
        if (gun == null || !gunId.equals(gun.getGunId(mainHandItem))
                || !scopeId.equals(gun.getAttachmentId(mainHandItem, AttachmentType.SCOPE))) {
            setupPlayer(minecraft, player, buildGun(gunId, scopeId), gunId, scopeId);
            return;
        }
        syncServerRotation(minecraft, player.getUUID());
    }

    private static void prepareClientScopeState(IClientPlayerGunOperator operator) {
        LocalPlayerDataHolder data = operator.getDataHolder();
        data.clientStateLock = false;
        data.lockedCondition = null;
        data.isCharging = false;
        data.chargeProgress = 0.0F;
        data.clientDrawTimestamp = System.currentTimeMillis() - 10_000L;
        LocalPlayerDataHolder.clientClickButtonTimestamp = -1L;
    }

    private static void forceClientAimingProgress(IClientPlayerGunOperator operator, float progress) {
        LocalPlayerDataHolder data = operator.getDataHolder();
        LocalPlayerDataHolder.oldAimingProgress = progress;
        data.clientAimingProgress = progress;
    }

    private static void forceLocalSyncedReady(LocalPlayer player) {
        ModSyncedEntityData.DRAW_COOL_DOWN_KEY.setValue(player, 0L);
        ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.setValue(player, 0L);
        ModSyncedEntityData.MELEE_COOL_DOWN_KEY.setValue(player, 0L);
        ModSyncedEntityData.IS_BOLTING_KEY.setValue(player, false);
        ModSyncedEntityData.RELOAD_STATE_KEY.setValue(player, new ReloadState());
        ModSyncedEntityData.SPRINT_TIME_KEY.setValue(player, 0.0F);
    }

    private static void applyStablePose(Minecraft minecraft, LocalPlayer player) {
        player.setNoGravity(true);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(TEST_POSITION);
        player.setYRot(TEST_YAW);
        player.setXRot(TEST_PITCH);
        player.yRotO = TEST_YAW;
        player.xRotO = TEST_PITCH;
        player.setYHeadRot(TEST_YAW);
        player.setYBodyRot(TEST_YAW);
        player.yHeadRotO = TEST_YAW;
        player.yBodyRotO = TEST_YAW;
        player.setSprinting(false);
        syncServerRotation(minecraft, player.getUUID());
    }

    private static void syncServerInventoryAndDraw(Minecraft minecraft, UUID playerId, ItemStack gunStack) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            if (serverPlayer == null) {
                return;
            }
            serverPlayer.getInventory().setSelectedSlot(0);
            serverPlayer.getInventory().setItem(0, gunStack.copy());
            applyServerPose(serverPlayer);
            IGunOperator.fromLivingEntity(serverPlayer).draw(() -> serverPlayer.getInventory().getItem(0));
        });
    }

    private static void syncServerRotation(Minecraft minecraft, UUID playerId) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            if (serverPlayer != null) {
                applyServerPose(serverPlayer);
            }
        });
    }

    private static void applyServerPose(ServerPlayer player) {
        player.setNoGravity(true);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(TEST_POSITION.x, TEST_POSITION.y, TEST_POSITION.z);
        player.setYRot(TEST_YAW);
        player.setXRot(TEST_PITCH);
        player.yRotO = TEST_YAW;
        player.xRotO = TEST_PITCH;
        player.setYHeadRot(TEST_YAW);
        player.setYBodyRot(TEST_YAW);
        player.yHeadRotO = TEST_YAW;
        player.yBodyRotO = TEST_YAW;
        player.setSprinting(false);
    }

    private static void logEvery(String phaseName, Identifier scopeId, LocalPlayer player) {
        if (phaseTicks % 10 == 0) {
            ScopeRenderDebug.autoTest(phaseName, scopeId, aimingProgress(player), player.getMainHandItem());
        }
    }

    private static float aimingProgress(LocalPlayer player) {
        return IClientPlayerGunOperator.fromLocalPlayer(player)
                .getClientAimingProgress(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    private static void advance(Identifier scopeId, LocalPlayer player) {
        ScopeRenderDebug.autoTest("scope_done", scopeId, aimingProgress(player), player.getMainHandItem());
        phase = Phase.SETUP;
        phaseTicks = 0;
        caseIndex++;
        if (caseIndex >= TEST_CASES.length) {
            finished = true;
            ScopeRenderDebug.autoTest("finished", scopeId, aimingProgress(player), player.getMainHandItem());
        }
    }

    private static boolean isHolding(Phase checkedPhase) {
        return !HOLD_PHASE.isEmpty() && HOLD_PHASE.equalsIgnoreCase(checkedPhase.name());
    }

    private static void clearScreen(Minecraft minecraft) {
        minecraft.gui.setPauseScreen(false, false);
        MinecraftGuiCompat.setScreen(null);
    }

    private enum Phase {
        SETUP,
        NON_ADS,
        ADS_TRANSITION,
        FULL_ADS,
        ADS_EXIT
    }

    private record ScopeCase(Identifier gunId, Identifier scopeId) {
    }
}
