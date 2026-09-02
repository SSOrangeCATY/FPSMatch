package com.ptcrys.fpsmatch.common.client.spec;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.ptcrys.fpsmatch.mixin.spec.teammate.CameraInvokerMixin;

/** Client camera state for fixed death-location and C4 orbit views. */
public final class SpectatorCameraController {

    /** 每帧向目标角度收敛的速度系数(越大越跟手、越小越平滑)。 */
    private static final float SMOOTH_RATE = 22.0F;
    /** 轨道半径向安全半径收近的速度(格/秒)：较快，避免穿入障碍。 */
    private static final double RADIUS_IN_RATE = 12.0D;
    /** 轨道半径向外恢复的速度(格/秒)：较慢，避免从墙边"弹出"造成跳变。 */
    private static final double RADIUS_OUT_RATE = 4.0D;
    /** 相机与障碍表面之间的最小间距(格)。 */
    private static final double RADIUS_INSET = 0.18D;
    /** 相机允许的最小轨道半径，防止锚点贴地/卡墙时半径缩到 0。 */
    private static final double RADIUS_MIN = 0.4D;

    private static float yaw;
    private static float pitch;
    private static float targetYaw;
    private static float targetPitch;
    private static long lastSmoothNs;

    /** 当前生效的轨道半径(随朝向动态收近/恢复)，在移动时实时判断障碍，避免事后回推的顿挫。 */
    private static double smoothRadius = SpectatorCameraMath.DEFAULT_ORBIT_RADIUS;
    private static long lastRadiusNs;

    private SpectatorCameraController() {}

    public static void reset() {
        yaw = 0.0f;
        pitch = 0.0f;
        targetYaw = 0.0f;
        targetPitch = 0.0f;
        lastSmoothNs = 0L;
        smoothRadius = SpectatorCameraMath.DEFAULT_ORBIT_RADIUS;
        lastRadiusNs = 0L;
    }

    public static void applyAngles(float deltaYaw, float deltaPitch) {
        if (!SpectateState.isRestricted()) return;
        targetYaw += deltaYaw;
        targetPitch = SpectatorCameraMath.clampPitch(targetPitch + deltaPitch);
    }

    public static void setAngles(float newYaw, float newPitch) {
        targetYaw = newYaw;
        targetPitch = SpectatorCameraMath.clampPitch(newPitch);
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    /** 每帧按帧时间把当前角度向目标平滑收敛，吸收鼠标采样抖动，消除旋转卡顿。 */
    private static void smoothAngles() {
        long now = System.nanoTime();
        float dt = lastSmoothNs == 0L ? 0.016F : Math.min(0.05F, (now - lastSmoothNs) / 1_000_000_000.0F);
        lastSmoothNs = now;
        float snap = Math.min(1.0F, SMOOTH_RATE * dt);

        // 偏航角取最短路径，避免跨越 0°/360° 时反向狂转
        float dy = targetYaw - yaw;
        dy = ((dy + 180.0F) % 360.0F + 360.0F) % 360.0F - 180.0F;
        yaw += dy * snap;
        pitch += (targetPitch - pitch) * snap;
    }

    /** 在移动时就根据当前朝向做方块检测：安全半径 = 锚点到最近障碍的距离 - 间距。 */
    private static double computeSafeRadius(Vec3 anchor, Vec3 dir, double baseRadius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return baseRadius;
        }
        Vec3 end = anchor.add(dir.x * baseRadius, dir.y * baseRadius, dir.z * baseRadius);
        HitResult hit = mc.level.clip(new ClipContext(anchor, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return baseRadius;
        }
        double distance = anchor.distanceTo(((BlockHitResult) hit).getLocation());
        return Math.max(RADIUS_MIN, distance - RADIUS_INSET);
    }

    /** 当前半径向安全半径收敛：收近快、恢复慢，既防穿模又避免边界跳变。 */
    private static double approachRadius(double target) {
        long now = System.nanoTime();
        float dt = lastRadiusNs == 0L ? 0.016F : Math.min(0.05F, (now - lastRadiusNs) / 1_000_000_000.0F);
        lastRadiusNs = now;
        double diff = target - smoothRadius;
        if (diff == 0.0D) {
            return target;
        }
        double rate = diff < 0.0D ? RADIUS_IN_RATE : RADIUS_OUT_RATE;
        double maxStep = rate * dt;
        if (Math.abs(diff) <= maxStep) {
            return target;
        }
        return smoothRadius + Math.signum(diff) * maxStep;
    }

    public static void applyToCamera(Camera camera, float partialTick) {
        if (camera == null) return;
        SpectateTarget target = SpectateState.getTarget();
        if (target == null) return;
        smoothAngles();

        Vec3 anchor = target.anchor();
        double baseRadius = target.orbitRadius() > 0.0F ? target.orbitRadius() : SpectatorCameraMath.DEFAULT_ORBIT_RADIUS;

        // 单位方向(锚点→相机)，radius=1 的轨道点即方向向量
        Vec3 dir = SpectatorCameraMath.orbitPosition(anchor, yaw, pitch, 1.0F).subtract(anchor);
        // 移动时即判断障碍：半径随朝向动态收近/恢复，不再事后回推相机位置
        smoothRadius = approachRadius(computeSafeRadius(anchor, dir, baseRadius));

        Vec3 pos = SpectatorCameraMath.orbitPosition(anchor, yaw, pitch, (float) smoothRadius);
        ((CameraInvokerMixin) camera).invokeSetPosition(pos.x, pos.y, pos.z);
        // 看向锚点：旋转始终以 C4/死亡地点为枢轴
        Vec3 look = anchor.subtract(pos);
        float lookYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float lookPitch = (float) Math.toDegrees(
                Math.atan2(-look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
        ((CameraInvokerMixin) camera).invokeSetRotation(lookYaw, lookPitch);
    }

    public static Entity resolveEntity() {
        SpectateTarget target = SpectateState.getTarget();
        Minecraft mc = Minecraft.getInstance();
        return target == null || mc.level == null || target.entityId() < 0 ? null : mc.level.getEntity(target.entityId());
    }
}
