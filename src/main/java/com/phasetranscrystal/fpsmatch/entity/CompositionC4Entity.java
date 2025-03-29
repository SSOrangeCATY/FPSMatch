package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BlastModeMap;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.item.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.net.BombActionS2CPacket;
import com.phasetranscrystal.fpsmatch.net.BombDemolitionProgressS2CPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class CompositionC4Entity extends Entity implements TraceableEntity {
    private ChunkPos currentChunkPos;
    private static final EntityDataAccessor<Integer> DATA_FUSE_ID = SynchedEntityData.defineId(CompositionC4Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EXPLOSION_RADIUS = SynchedEntityData.defineId(CompositionC4Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DEMOLITION_STATE = SynchedEntityData.defineId(CompositionC4Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DEMOLITION_PROGRESS = SynchedEntityData.defineId(CompositionC4Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DELETE_TIME = SynchedEntityData.defineId(CompositionC4Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EXPLOSION_INTERACTION = SynchedEntityData.defineId(CompositionC4Entity.class, EntityDataSerializers.INT);
    private static final int DEFAULT_FUSE_TIME = 800; // 40秒
    private static final int DEFAULT_EXPLOSION_RADIUS = 60;
    @Nullable
    private Player owner;
    @Nullable
    private Player demolisher;
    private BlastModeMap<?> map;
    private boolean deleting = false;
    private int soundPlayCount = 0;  // 用于记录播放次数

    public CompositionC4Entity(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.blocksBuilding = true;
        this.noCulling = true;
        this.setDemolitionProgress(0);
        this.setDeleteTime(0);
    }

    public CompositionC4Entity(Level pLevel, double pX, double pY, double pZ, @Nullable Player pOwner, @NotNull BlastModeMap<?> map) {
        this(EntityRegister.C4.get(), pLevel);
        this.setFuse(DEFAULT_FUSE_TIME);
        this.setExplosionRadius(DEFAULT_EXPLOSION_RADIUS);
        this.setPos(pX, pY, pZ);
        this.owner = pOwner;
        this.map = map;
        map.setBlasting(1);
    }

    public CompositionC4Entity(Level pLevel, Vec3 pos, @Nullable Player pOwner, @NotNull BlastModeMap<?> map, int fuseTime, int explosionRadius) {
        this(EntityRegister.C4.get(), pLevel);
        this.setFuse(fuseTime);
        this.setExplosionRadius(explosionRadius);
        this.setPos(pos);
        this.owner = pOwner;
        this.map = map;
        map.setBlasting(1);
    }
    public CompositionC4Entity(Level pLevel, Vec3 pos, @Nullable Player pOwner, @NotNull BlastModeMap<?> map, int fuseTime, int explosionRadius, Level.ExplosionInteraction explosionInteraction) {
        this(EntityRegister.C4.get(), pLevel);
        this.setExplosionInteraction(explosionInteraction);
        this.setFuse(fuseTime);
        this.setExplosionRadius(explosionRadius);
        this.setPos(pos);
        this.owner = pOwner;
        this.map = map;
        map.setBlasting(1);
    }
    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_FUSE_ID, DEFAULT_FUSE_TIME);
        this.entityData.define(DATA_EXPLOSION_RADIUS, DEFAULT_EXPLOSION_RADIUS);
        this.entityData.define(DATA_DEMOLITION_PROGRESS,0);
        this.entityData.define(DATA_DELETE_TIME,0);
        this.entityData.define(DATA_DEMOLITION_STATE, 0);
        this.entityData.define(DATA_EXPLOSION_INTERACTION, Level.ExplosionInteraction.NONE.ordinal());
    }


    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.level().isClientSide) {
            forceLoadChunk();
        }
    }

    private void forceLoadChunk() {
        ServerLevel serverLevel = (ServerLevel) this.level();
        currentChunkPos = new ChunkPos(this.blockPosition());
        serverLevel.getChunkSource().addRegionTicket(
                FPSMatch.ENTITY_CHUNK_TICKET,
                currentChunkPos,
                0,
                this.getUUID()
        );
    }
    private void unForceLoadChunk() {
        ServerLevel serverLevel = (ServerLevel) this.level();
        serverLevel.getChunkSource().removeRegionTicket(
                FPSMatch.ENTITY_CHUNK_TICKET,
                currentChunkPos,
                0,
                this.getUUID()
        );
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!this.level().isClientSide) {
            unForceLoadChunk();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {
        this.setFuse(pCompound.getInt("Fuse"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {
        pCompound.putInt("Fuse", this.getFuse());
    }

    public void tick() {
        if (!this.level().isClientSide) {
            ChunkPos newChunkPos = new ChunkPos(this.blockPosition());
            if (!newChunkPos.equals(currentChunkPos)) {
                unForceLoadChunk();
                currentChunkPos = newChunkPos;
                forceLoadChunk();
            }
        }

        if(this.getDeleteTime() >= 140){
            this.syncDemolitionProgress(0);
            this.discard();
        }

        if (soundPlayCount > 0 && soundPlayCount < 3) {
            // 如果音效播放次数小于3次
            this.playDefusingSound();
            soundPlayCount++;  // 增加播放次数
        }

        if(!this.level().isClientSide){
            if(this.deleting){
                int d = this.getDeleteTime() + 1;
                this.setDeleteTime(d);
                return;
            }

            if(this.map == null) {
                this.discard();
                return;
            }

            int i = this.getFuse() - 1;
            this.setFuse(i);
            if(demolisher == null){
                this.setDemolitionProgress(0);
            }else{
                this.setDemolitionProgress(this.getDemolitionProgress() + 1);
                if (demolisher instanceof ServerPlayer serverPlayer){
                    if(serverPlayer.gameMode.getGameModeForPlayer() == GameType.SPECTATOR){
                        this.syncDemolitionProgress(0);
                        this.demolisher = null;
                    }
                    if(i % 2 == 0 && i > 0){
                        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new BombActionS2CPacket());
                    }
                }
            }

            int j = 200;
            if(demolisher != null && this.demolisher.getInventory().countItem(FPSMItemRegister.BOMB_DISPOSAL_KIT.get()) >= 1){
                j = 100;
            }

            if(this.getDemolitionProgress() > j){
                this.setDemolitionProgress(0);
            }

            float k = (float) this.getDemolitionProgress() / j;
            if(k > 0 && demolisher != null){
                this.syncDemolitionProgress(k);
            }else {
                this.syncDemolitionProgress(0);
            }

            if(this.getDemolitionProgress() >= j){
                this.deleting = true;
                map.setBlasting(2);
                this.playDefusedSound();
                return;
            }

            if (i <= 0) {
                if (!this.level().isClientSide) {
                    this.explode();
                }
            }

            if(i < 200){
                if(i < 100){
                    if(i <= 20) this.playBeepSound();
                    if(i % 5 == 0){
                        this.playBeepSound();
                    }
                }else if( i % 10 == 0){
                    this.playBeepSound();
                }
            } else{
                if(i % 20 == 0){
                    this.playBeepSound();
                }
            }
        }

        if (!this.isNoGravity()) {
            this.setDeltaMovement(0,0,0);
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D)); // 重力
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
    }


    public void playBeepSound(){
//        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.VOICE, 3.0F, 1.0F);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), FPSMSoundRegister.beep.get(), SoundSource.VOICE, 3.0F, 0.8F);
    }
    public void playDefusingSound(){
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.VOICE, 3.0F, 2.0F);
    }
    public void playDefusedSound(){
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), FPSMSoundRegister.defused.get(), SoundSource.VOICE, 3.0F, 0.9F);
    }
    private void explode() {
        float explosionRadius = this.getExplosionRadius(); // 爆炸半径
        this.deleting = true;
        this.map.setExploded(true);
        this.syncDemolitionProgress(0);
        this.level().explode(this, this.getX(), this.getY(), this.getZ(), explosionRadius, this.explosionInteraction());
    }

    public void syncDemolitionProgress(float progress){
        BaseMap map = (BaseMap) this.map;
        if(map != null){
            map.getMapTeams().getJoinedPlayers().forEach((pUUID)->{
                ServerPlayer receiver = (ServerPlayer) this.level().getPlayerByUUID(pUUID);
                if(receiver != null){
                    BaseTeam team = map.getMapTeams().getTeamByPlayer(receiver);
                    if(team != null){
                        boolean flag = this.map.checkCanPlacingBombs(team.getFixedName());
                        if(!flag){
                            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> receiver), new BombDemolitionProgressS2CPacket(progress));
                        }
                    }
                }
            });

            map.getMapTeams().getSpecPlayers().forEach((pUUID)-> {
                ServerPlayer receiver = (ServerPlayer) this.level().getPlayerByUUID(pUUID);
                if (receiver != null) {
                    FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> receiver), new BombDemolitionProgressS2CPacket(progress));
                }
            });
        }
    }

    @Nullable
    public LivingEntity getOwner() {
        return this.owner;
    }

    protected float getEyeHeight(@NotNull Pose pPose, @NotNull EntityDimensions pSize) {
        return 0.15F;
    }

    public void setFuse(int pLife) {
        this.entityData.set(DATA_FUSE_ID, pLife);
    }

    public int getFuse() {
        return this.entityData.get(DATA_FUSE_ID);
    }

    public void setExplosionRadius(int radius){
        this.entityData.set(DATA_EXPLOSION_RADIUS, radius);
    }

    public int getExplosionRadius() {
        return this.entityData.get(DATA_EXPLOSION_RADIUS);
    }

    public int getDemolitionProgress(){
        return this.entityData.get(DATA_DEMOLITION_PROGRESS);
    }

    public void setDemolitionProgress(int progress){
        this.entityData.set(DATA_DEMOLITION_PROGRESS, progress);
    }

    public int getDeleteTime(){
        return this.entityData.get(DATA_DELETE_TIME);
    }

    public void setDeleteTime(int progress){
        this.entityData.set(DATA_DELETE_TIME, progress);
    }

    public BlastModeMap<?> getMap() {
        return map;
    }

    public void setDemolisher(@org.jetbrains.annotations.Nullable Player player){
        if(this.demolisher == null) {
            if (player != null && checkDemolisher(player)) {
                this.playDefusingSound();
                this.demolisher = player;
            }
        }
    }

    public void resetDemolisher(){
        this.demolisher = null;
    }

    public AABB getR(){
        AABB ab = this.getBoundingBox();
        int r = 3;
        return new AABB(ab.minX - r, ab.minY - r, ab.minZ - r, ab.maxX + r, ab.maxY + r, ab.maxZ + r);
    }
    public boolean checkDemolisher(Player player){
        return this.getR().contains(player.getPosition(0));
    }

    @Nullable
    public Player getDemolisher() {
        return demolisher;
    }

    public int demolitionStates() {
        return this.entityData.get(DATA_DEMOLITION_STATE);
    }

    public void setDemolitionStates(int demolitionStates) {
        this.entityData.set(DATA_DEMOLITION_STATE, demolitionStates);
    }

    public void setExplosionInteraction(Level.ExplosionInteraction explosionInteraction) {
        this.entityData.set(DATA_DEMOLITION_STATE, explosionInteraction.ordinal());
    }
    public Level.ExplosionInteraction explosionInteraction() {
        return Level.ExplosionInteraction.values()[this.entityData.get(DATA_EXPLOSION_INTERACTION)];
    }


}