package com.phasetranscrystal.fpsmatch.mixin.ammo;

import com.phasetranscrystal.fpsmatch.common.entity.throwable.SmokeShellEntity;
import com.phasetranscrystal.fpsmatch.compat.IPassThroughEntity;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class AmmoEntityMixin implements IPassThroughEntity {
    @Unique
    private boolean fpsmatch$passedThroughWall = false;

    @Unique
    private boolean fpsmatch$passedThroughSmoke = false;

    @Unique
    private boolean fpsmatch$scoped = false;

    @Inject(method = "onBulletTick", at = @At(value = "HEAD"))
    private void fpsmatch$checkPassedSmoke(CallbackInfo ci) {
        EntityKineticBullet bullet = (EntityKineticBullet)(Object)this;
        if (bullet.level().isClientSide()) return;
        if (fpsmatch$passedThroughSmoke) return;

        List<Entity> entities = bullet.level().getEntities(bullet, bullet.getBoundingBox().expandTowards(bullet.getDeltaMovement()).inflate(16.0));
        if (entities.isEmpty()) return;

        AABB checker = bullet.getBoundingBox().expandTowards(bullet.getDeltaMovement()).inflate(1D);

        if (fpsmatch$isPassedSmoke(entities, checker)) {
            fpsmatch$passedThroughSmoke = true;
        }
    }

    @Unique
    private boolean fpsmatch$isPassedSmoke(List<Entity> entities, AABB checker) {
        List<SmokeShellEntity> smokes = entities.stream()
                .filter(entity -> entity instanceof SmokeShellEntity)
                .map(entity -> (SmokeShellEntity)entity)
                .toList();

        for (SmokeShellEntity smoke : smokes) {
            if (smoke.isInSmokeArea(checker)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean fpsmatch$isWall() {
        return this.fpsmatch$passedThroughWall;
    }

    @Override
    public void fpsmatch$setThroughWall(boolean passed) {
        this.fpsmatch$passedThroughWall = passed;
    }

    @Override
    public boolean fpsmatch$isSmoke() {
        return this.fpsmatch$passedThroughSmoke;
    }

    @Override
    public void fpsmatch$setThroughSmoke(boolean passed) {
        this.fpsmatch$passedThroughSmoke = passed;
    }

    @Override
    public boolean fpsmatch$isScoped() {
        return this.fpsmatch$scoped;
    }

    @Override
    public void fpsmatch$setScoped(boolean scoped) {
        this.fpsmatch$scoped = scoped;
    }
}
