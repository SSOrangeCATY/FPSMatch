package com.tacz.guns.api.event.common;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.LogicalSide;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 生物被枪械子弹击杀时触发的事件
 */
public class EntityKillByGunEvent extends Event implements KubeJSGunEventPoster<EntityKillByGunEvent>{
    private final Entity bullet;
    private final @Nullable LivingEntity killedEntity;
    private final @Nullable LivingEntity attacker;
    private final Identifier gunId;
    private final Identifier gunDisplayId;
    private final float baseDamage;
    private final DamageSource nonApPartDamageSource;
    private final DamageSource apPartDamageSource;
    private final boolean isHeadShot;
    private final float headshotMultiplier;
    private final LogicalSide logicalSide;

    public EntityKillByGunEvent(Entity bullet, @Nullable LivingEntity hurtEntity, @Nullable LivingEntity attacker,
                                Identifier gunId, Identifier gunDisplayId, float baseDamage, @Nullable Pair<DamageSource, DamageSource> sources,
                                boolean isHeadShot, float headshotMultiplier, LogicalSide logicalSide) {
        this.bullet = bullet;
        this.killedEntity = hurtEntity;
        this.attacker = attacker;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.baseDamage = baseDamage;
        this.nonApPartDamageSource = Optional.ofNullable(sources).map(Pair::getLeft).orElse(null);
        this.apPartDamageSource = Optional.ofNullable(sources).map(Pair::getRight).orElse(null);
        this.isHeadShot = isHeadShot;
        this.headshotMultiplier = headshotMultiplier;
        this.logicalSide = logicalSide;
        postEventToKubeJS(this);
    }

    /**
     * 在逻辑客户端不保证能用
     */
    public Entity getBullet() {
        return bullet;
    }

    @Nullable
    public LivingEntity getKilledEntity() {
        return killedEntity;
    }

    @Nullable
    public LivingEntity getAttacker() {
        return attacker;
    }

    public Identifier getGunId() {
        return gunId;
    }

    public float getBaseDamage() {
        return baseDamage;
    }

    public DamageSource getDamageSource(GunDamageSourcePart part) {
        if (logicalSide.isClient()) {
            throw new UnsupportedOperationException("DamageSource about gun hit is not available on client side!");
        }
        return part == GunDamageSourcePart.ARMOR_PIERCING ? apPartDamageSource : nonApPartDamageSource;
    }

    public boolean isHeadShot() {
        return isHeadShot;
    }

    public float getHeadshotMultiplier() {
        return headshotMultiplier;
    }

    public LogicalSide getLogicalSide() {
        return logicalSide;
    }

    public Identifier getGunDisplayId() {
        return gunDisplayId;
    }

    @Nullable
    public Identifier getAmmoId() {
        return bullet instanceof EntityKineticBullet kineticBullet ? kineticBullet.getAmmoId() : null;
    }

    public String getAmmoSlotId() {
        return bullet instanceof EntityKineticBullet kineticBullet ? kineticBullet.getAmmoSlotId() : "";
    }

    public String getRuntimeItemId() {
        return bullet instanceof EntityKineticBullet kineticBullet ? kineticBullet.getRuntimeItemId() : "";
    }

    public long getShotId() {
        return bullet instanceof EntityKineticBullet kineticBullet ? kineticBullet.getShotId() : 0L;
    }
}
