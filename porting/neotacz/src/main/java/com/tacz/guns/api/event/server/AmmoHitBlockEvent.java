package com.tacz.guns.api.event.server;

import com.tacz.guns.api.event.common.KubeJSGunEventPoster;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * 子弹击中方块时触发的事件，目前仅在服务端触发
 */
public class AmmoHitBlockEvent extends Event implements KubeJSGunEventPoster<AmmoHitBlockEvent>, ICancellableEvent {
    private final Level level;
    private final BlockHitResult hitResult;
    private final BlockState state;
    private final EntityKineticBullet ammo;
    private final Vec3 tickStart;
    private final Vec3 tickEnd;

    public AmmoHitBlockEvent(Level level, BlockHitResult hitResult, BlockState state, EntityKineticBullet ammo) {
        this(level, hitResult, state, ammo, ammo.position(), hitResult.getLocation());
    }

    public AmmoHitBlockEvent(Level level, BlockHitResult hitResult, BlockState state, EntityKineticBullet ammo, Vec3 tickStart, Vec3 tickEnd) {
        this.level = level;
        this.hitResult = hitResult;
        this.state = state;
        this.ammo = ammo;
        this.tickStart = tickStart;
        this.tickEnd = tickEnd;
        postServerEventToKubeJS(this);
    }

    public Level getLevel() {
        return level;
    }

    public BlockHitResult getHitResult() {
        return hitResult;
    }

    public BlockState getState() {
        return state;
    }

    public EntityKineticBullet getAmmo() {
        return ammo;
    }

    public Vec3 getTickStart() {
        return tickStart;
    }

    public Vec3 getTickEnd() {
        return tickEnd;
    }
}
