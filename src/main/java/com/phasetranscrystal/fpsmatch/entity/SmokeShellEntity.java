package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.entity.BaseProjectileLifeTimeEntity;
import com.phasetranscrystal.fpsmatch.cs.CSGameMap;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public class SmokeShellEntity extends BaseProjectileLifeTimeEntity {
    private static final EntityDataAccessor<ParticleOptions> PARTICLE_OPTIONS = SynchedEntityData.defineId(SmokeShellEntity.class, EntityDataSerializers.PARTICLE);
    private static final EntityDataAccessor<Integer> Particle_COOLDOWN = SynchedEntityData.defineId(SmokeShellEntity.class, EntityDataSerializers.INT);
    public static final int MAX_LIFETIME = 300;
    private static final Vector3f T = new Vector3f(1, 0.75f, 0.25f);
    private static final Vector3f CT = new Vector3f(0.25f, 0.55f, 1);
    public SmokeShellEntity(EntityType<? extends SmokeShellEntity> type, Level level) {
        super(type, level);
    }

    public SmokeShellEntity(LivingEntity shooter, Level level) {
        super(EntityRegister.SMOKE_SHELL.get(), shooter, level);
        this.noCulling = true;
        setTimeLeft(MAX_LIFETIME);
        setTimeoutTicks(-1);
        if(this.getOwner() instanceof Player player){
            BaseMap baseMap = FPSMCore.getInstance().getMapByPlayer(player);
            if(baseMap instanceof CSGameMap csGameMap){
                BaseTeam baseTeam = csGameMap.getMapTeams().getTeamByPlayer(player);
                if(baseTeam != null){
                    this.setParticleOptions(new DustParticleOptions(baseTeam.name.equals("ct") ? CT : T , 10F));
                }
            }
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PARTICLE_OPTIONS, new DustParticleOptions(new Vector3f(1, 1, 1), 10F));
        entityData.define(Particle_COOLDOWN, 0);
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return FPSMItemRegister.SMOKE_SHELL.get();
    }

    @Override
    protected void onActiveTimeExpired() {
        spawnExpireParticles();
        discard();
    }

    @Override
    protected void onActiveTick(){
        if(this.getParticleCoolDown() > 0){
            this.setParticleCoolDown(this.getParticleCoolDown() - 1);
        }
    };

    private void spawnExpireParticles() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    getX(), getY(), getZ(), 30,
                    0.5, 0.5, 0.5, 0.2);
        }
    }

    public int getParticleCoolDown() {
        return entityData.get(Particle_COOLDOWN);
    }

    public void setParticleCoolDown(int particleCoolDown) {
        entityData.set(Particle_COOLDOWN, particleCoolDown);
    }

    public ParticleOptions getParticleOptions(){
        return entityData.get(PARTICLE_OPTIONS);
    }
    public void setParticleOptions(ParticleOptions options){
        entityData.set(PARTICLE_OPTIONS, options);
    }

}
