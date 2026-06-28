package com.tacz.guns.client.debug;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.renderer.entity.BulletTracerDebug;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.util.MinecraftGuiCompat;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.UUID;

@EventBusSubscriber(value = Dist.CLIENT)
public final class TracerDebugAutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("tacz.debug.tracer.autotest")
            && Boolean.getBoolean("tacz.debug.tracer")
            && Boolean.getBoolean("mcagent.testMode");
    private static final boolean NEAR_ONLY = Boolean.getBoolean("tacz.debug.tracer.nearOnly");
    private static final Identifier TEST_GUN_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "m4a1");
    private static final float TEST_PITCH = -8.0F;
    private static final Vec3 TEST_POSITION = new Vec3(72.5, 120.0, -87.5);
    private static final int SETUP_WAIT_TICKS = 60;
    private static final int DIRECTION_STABILIZE_TICKS = 20;
    private static final int SHOT_SPACING_TICKS = 10;
    private static final int SHOT_RETRY_TICKS = 5;
    private static final int MAX_SHOT_ATTEMPTS = 60;
    private static final int SHOTS_PER_DIRECTION = 3;
    private static final int SHOTS_PER_NEAR_WALL = 1;
    private static final int POST_DIRECTION_WAIT_TICKS = 35;
    private static final int[] NEAR_WALL_DISTANCES = {1, 2, 3};
    private static final DirectionSample[] DIRECTIONS = {
            new DirectionSample("-Z", 180.0F),
            new DirectionSample("+X", -90.0F),
            new DirectionSample("+Z", 0.0F),
            new DirectionSample("-X", 90.0F)
    };

    private enum TestPhase {
        OPEN_AIR,
        NEAR_WALL
    }

    private static int ticks;
    private static boolean setupComplete;
    private static int waitTicks;
    private static TestPhase phase = NEAR_ONLY ? TestPhase.NEAR_WALL : TestPhase.OPEN_AIR;
    private static int directionIndex;
    private static int directionTicks;
    private static int shotIndex;
    private static int shotAttempts;
    private static int wallDistanceIndex;
    private static boolean nearWallReady;
    private static boolean finished;

    private TracerDebugAutoTest() {
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
            clearBlockingScreen(minecraft);
            BulletTracerDebug.autoTest("respawn", currentScenarioName(), currentYaw(), TEST_PITCH, shotIndex, null, player.position());
            return;
        }
        if (screen != null) {
            if (minecraft.level != null && ticks > SETUP_WAIT_TICKS) {
                clearBlockingScreen(minecraft);
                if (ticks % 40 == 0) {
                    BulletTracerDebug.autoTest("ignore_screen:" + screen.getClass().getName(), currentScenarioName(), currentYaw(), TEST_PITCH, shotIndex, null, player.position());
                }
            } else {
                if (ticks % 40 == 0) {
                    BulletTracerDebug.autoTest("waiting_screen:" + screen.getClass().getName(), currentScenarioName(), currentYaw(), TEST_PITCH, shotIndex, null, player.position());
                }
                return;
            }
        }
        if (TimelessAPI.getCommonGunIndex(TEST_GUN_ID).isEmpty() || TimelessAPI.getClientGunIndex(TEST_GUN_ID).isEmpty()) {
            if (ticks % 40 == 0) {
                BulletTracerDebug.autoTest("waiting_index", currentScenarioName(), currentYaw(), TEST_PITCH, shotIndex, null, player.position());
            }
            return;
        }

        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        if (!setupComplete) {
            ItemStack gunStack = buildTestGun();
            if (gunStack.isEmpty()) {
                if (ticks % 40 == 0) {
                    BulletTracerDebug.autoTest("waiting_gun_stack", currentScenarioName(), currentYaw(), TEST_PITCH, shotIndex, null, player.position());
                }
                return;
            }
            clearNearWallArea(minecraft, player.getUUID());
            setupPlayer(minecraft, player, gunStack);
            setupComplete = true;
            waitTicks = SETUP_WAIT_TICKS;
            BulletTracerDebug.autoTest("setup", currentScenarioName(), currentYaw(), TEST_PITCH, shotIndex, null, player.position());
            return;
        }

        DirectionSample direction = DIRECTIONS[directionIndex];
        applyRotation(minecraft, player, direction.yaw(), TEST_PITCH);
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (directionTicks == 0) {
            BulletTracerDebug.autoTest("enter_direction", currentScenarioName(), direction.yaw(), TEST_PITCH, shotIndex, null, player.position());
        }
        directionTicks++;
        if (directionTicks < DIRECTION_STABILIZE_TICKS) {
            return;
        }

        if (phase == TestPhase.NEAR_WALL && !nearWallReady) {
            setupNearWall(minecraft, player.getUUID(), direction, currentWallDistance());
            nearWallReady = true;
            waitTicks = 3;
            BulletTracerDebug.autoTest("setup_near_wall", currentScenarioName(), direction.yaw(), TEST_PITCH, shotIndex, null, player.position());
            return;
        }

        int shootWindowTick = directionTicks - DIRECTION_STABILIZE_TICKS;
        int shotsForScenario = phase == TestPhase.OPEN_AIR ? SHOTS_PER_DIRECTION : SHOTS_PER_NEAR_WALL;
        int targetShootTick = shotIndex * SHOT_SPACING_TICKS;
        if (shotIndex < shotsForScenario && shootWindowTick >= targetShootTick && (shootWindowTick - targetShootTick) % SHOT_RETRY_TICKS == 0) {
            ensureTestGun(minecraft, player);
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
            prepareClientShootState(operator);
            forceLocalSyncedReady(player);
            ShootResult result = operator.shoot();
            BulletTracerDebug.autoTest("shoot", currentScenarioName(), direction.yaw(), TEST_PITCH, shotIndex, result, player.position());
            if (result == ShootResult.SUCCESS || ++shotAttempts >= MAX_SHOT_ATTEMPTS) {
                if (result != ShootResult.SUCCESS) {
                    BulletTracerDebug.autoTest("shoot_give_up", currentScenarioName(), direction.yaw(), TEST_PITCH, shotIndex, result, player.position());
                }
                shotIndex++;
                shotAttempts = 0;
            }
            return;
        }

        int doneTick = DIRECTION_STABILIZE_TICKS + shotsForScenario * SHOT_SPACING_TICKS + POST_DIRECTION_WAIT_TICKS;
        if (directionTicks >= doneTick) {
            BulletTracerDebug.autoTest("leave_direction", currentScenarioName(), direction.yaw(), TEST_PITCH, shotIndex, null, player.position());
            advanceScenario(minecraft, player.getUUID(), direction.yaw());
        }
    }

    private static void setupPlayer(Minecraft minecraft, LocalPlayer player, ItemStack gunStack) {
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, gunStack.copy());
        player.setSprinting(false);
        player.setNoGravity(true);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(TEST_POSITION);
        applyRotation(minecraft, player, currentYaw(), TEST_PITCH);
        syncServerInventoryAndDraw(minecraft, player.getUUID(), gunStack.copy(), currentYaw(), TEST_PITCH);
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        operator.getDataHolder().reset();
        operator.draw(ItemStack.EMPTY);
    }

    private static void ensureTestGun(Minecraft minecraft, LocalPlayer player) {
        ItemStack mainHandItem = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(mainHandItem);
        if (gun == null || !TEST_GUN_ID.equals(gun.getGunId(mainHandItem))) {
            setupPlayer(minecraft, player, buildTestGun());
            return;
        }
        syncServerRotation(minecraft, player.getUUID(), currentYaw(), TEST_PITCH);
    }

    private static ItemStack buildTestGun() {
        FireMode fireMode = TimelessAPI.getCommonGunIndex(TEST_GUN_ID)
                .map(index -> index.getGunData().getFireModeSet().contains(FireMode.SEMI)
                        ? FireMode.SEMI
                        : index.getGunData().getFireModeSet().getFirst())
                .orElse(FireMode.SEMI);
        int ammoCount = TimelessAPI.getCommonGunIndex(TEST_GUN_ID)
                .map(index -> Math.max(index.getGunData().getAmmoAmount() * 4, 120))
                .orElse(120);
        ItemStack stack = GunItemBuilder.create()
                .setId(TEST_GUN_ID)
                .setFireMode(fireMode)
                .setAmmoCount(ammoCount)
                .setAmmoInBarrel(true)
                .build();
        if (stack.isEmpty()) {
            stack = GunItemBuilder.create()
                    .setId(TEST_GUN_ID)
                    .setFireMode(fireMode)
                    .setAmmoCount(ammoCount)
                    .setAmmoInBarrel(true)
                    .forceBuild();
        }
        return stack;
    }

    private static void prepareClientShootState(IClientPlayerGunOperator operator) {
        LocalPlayerDataHolder data = operator.getDataHolder();
        data.isShootRecorded = true;
        data.clientStateLock = false;
        data.lockedCondition = null;
        data.isCharging = false;
        data.chargeProgress = 0.0F;
        data.clientDrawTimestamp = System.currentTimeMillis() - 10_000L;
        LocalPlayerDataHolder.clientClickButtonTimestamp = -1L;
    }

    private static void forceLocalSyncedReady(LocalPlayer player) {
        ModSyncedEntityData.DRAW_COOL_DOWN_KEY.setValue(player, 0L);
        ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.setValue(player, 0L);
        ModSyncedEntityData.MELEE_COOL_DOWN_KEY.setValue(player, 0L);
        ModSyncedEntityData.IS_BOLTING_KEY.setValue(player, false);
        ModSyncedEntityData.RELOAD_STATE_KEY.setValue(player, new ReloadState());
        ModSyncedEntityData.SPRINT_TIME_KEY.setValue(player, 0.0F);
    }

    private static void clearBlockingScreen(Minecraft minecraft) {
        minecraft.gui.setPauseScreen(false, false);
        MinecraftGuiCompat.setScreen(null);
    }

    private static void applyRotation(Minecraft minecraft, LocalPlayer player, float yaw, float pitch) {
        player.setNoGravity(true);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(TEST_POSITION);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.yHeadRotO = yaw;
        player.yBodyRotO = yaw;
        syncServerRotation(minecraft, player.getUUID(), yaw, pitch);
    }

    private static void syncServerInventoryAndDraw(Minecraft minecraft, UUID playerId, ItemStack gunStack, float yaw, float pitch) {
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
            applyServerRotation(serverPlayer, yaw, pitch);
            IGunOperator.fromLivingEntity(serverPlayer).draw(() -> serverPlayer.getInventory().getItem(0));
        });
    }

    private static void syncServerRotation(Minecraft minecraft, UUID playerId, float yaw, float pitch) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            if (serverPlayer != null) {
                applyServerRotation(serverPlayer, yaw, pitch);
            }
        });
    }

    private static void setupNearWall(Minecraft minecraft, UUID playerId, DirectionSample direction, int distance) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            if (serverPlayer == null) {
                return;
            }
            if (!(serverPlayer.level() instanceof ServerLevel level)) {
                return;
            }
            clearNearWallArea(level);
            applyServerRotation(serverPlayer, direction.yaw(), TEST_PITCH);
            Vec3 forward = horizontalForward(direction.yaw());
            BlockPos base = BlockPos.containing(TEST_POSITION.add(forward.scale(distance)).add(0.0, 1.5, 0.0));
            boolean xDominant = Math.abs(forward.x) > Math.abs(forward.z);
            for (int lateral = -2; lateral <= 2; lateral++) {
                for (int vertical = -2; vertical <= 2; vertical++) {
                    BlockPos pos = xDominant
                            ? base.offset(0, vertical, lateral)
                            : base.offset(lateral, vertical, 0);
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                }
            }
        });
    }

    private static void clearNearWallArea(Minecraft minecraft, UUID playerId) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            if (serverPlayer != null && serverPlayer.level() instanceof ServerLevel level) {
                clearNearWallArea(level);
            }
        });
    }

    private static void clearNearWallArea(ServerLevel level) {
        BlockPos center = BlockPos.containing(TEST_POSITION.add(0.0, 1.5, 0.0));
        for (int x = -5; x <= 5; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.STONE)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static Vec3 horizontalForward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    private static void advanceScenario(Minecraft minecraft, UUID playerId, float yaw) {
        if (phase == TestPhase.NEAR_WALL) {
            clearNearWallArea(minecraft, playerId);
        }
        directionTicks = 0;
        shotIndex = 0;
        shotAttempts = 0;
        nearWallReady = false;

        if (phase == TestPhase.OPEN_AIR) {
            directionIndex++;
            if (directionIndex >= DIRECTIONS.length) {
                phase = TestPhase.NEAR_WALL;
                directionIndex = 0;
                wallDistanceIndex = 0;
            }
            return;
        }

        wallDistanceIndex++;
        if (wallDistanceIndex >= NEAR_WALL_DISTANCES.length) {
            wallDistanceIndex = 0;
            directionIndex++;
        }
        if (directionIndex >= DIRECTIONS.length) {
            finished = true;
            BulletTracerDebug.autoTest("finished", currentScenarioName(), yaw, TEST_PITCH, shotIndex, null, TEST_POSITION);
        }
    }

    private static void applyServerRotation(ServerPlayer player, float yaw, float pitch) {
        player.setNoGravity(true);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(TEST_POSITION.x, TEST_POSITION.y, TEST_POSITION.z);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.yHeadRotO = yaw;
        player.yBodyRotO = yaw;
        player.setSprinting(false);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static String currentDirectionName() {
        return DIRECTIONS[Math.min(directionIndex, DIRECTIONS.length - 1)].name();
    }

    private static float currentYaw() {
        return DIRECTIONS[Math.min(directionIndex, DIRECTIONS.length - 1)].yaw();
    }

    private static int currentWallDistance() {
        return NEAR_WALL_DISTANCES[Math.min(wallDistanceIndex, NEAR_WALL_DISTANCES.length - 1)];
    }

    private static String currentScenarioName() {
        if (phase == TestPhase.NEAR_WALL) {
            return currentDirectionName() + ":wall" + currentWallDistance();
        }
        return currentDirectionName();
    }

    private record DirectionSample(String name, float yaw) {
    }
}
