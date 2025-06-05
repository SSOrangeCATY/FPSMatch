package com.phasetranscrystal.fpsmatch.entity.drop;

import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.ShopData;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.entity.EntityRegister;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class MatchDropEntity extends Entity {
    public static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(MatchDropEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(MatchDropEntity.class, EntityDataSerializers.ITEM_STACK);
    private int pickupDelay;
    public MatchDropEntity(Level pLevel, ItemStack itemStack, DropType type) {
        super(EntityRegister.MATCH_DROP_ITEM.get(), pLevel);
        this.pickupDelay = 20;
        this.setItem(itemStack);
        this.setDataType(type);
    }

    public MatchDropEntity(EntityType<? extends MatchDropEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }


    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TYPE, 3);
        this.entityData.define(DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        if (this.getItem().isEmpty()) {
            this.discard();
        } else {
            super.tick();
            if (this.pickupDelay > 0 && this.pickupDelay != 32767) {
                --this.pickupDelay;
            }

            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            Vec3 vec3 = this.getDeltaMovement();
            float f = this.getEyeHeight() - 0.11111111F;
            net.minecraftforge.fluids.FluidType fluidType = this.getMaxHeightFluidType();
            if (!fluidType.isAir() && !fluidType.isVanilla() && this.getFluidTypeHeight(fluidType) > (double)f){
                this.setDeltaMovement(vec3.x * (double)0.99F, vec3.y + (double)(vec3.y < (double)0.06F ? 5.0E-4F : 0.0F), vec3.z * (double)0.99F);
            }
            else
            if (this.isInWater() && this.getFluidHeight(FluidTags.WATER) > (double)f) {
                this.setUnderwaterMovement();
            } else if (this.isInLava() && this.getFluidHeight(FluidTags.LAVA) > (double)f) {
                this.setUnderLavaMovement();
            } else if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            }

            if (this.level().isClientSide) {
                this.noPhysics = false;
            } else {
                this.noPhysics = !this.level().noCollision(this, this.getBoundingBox().deflate(1.0E-7D));
                if (this.noPhysics) {
                    this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.getZ());
                }
            }

            if (!this.onGround() || this.getDeltaMovement().horizontalDistanceSqr() > (double)1.0E-5F || (this.tickCount + this.getId()) % 4 == 0) {
                this.move(MoverType.SELF, this.getDeltaMovement());
                float f1 = 0.98F;
                if (this.onGround()) {
                    BlockPos groundPos = getBlockPosBelowThatAffectsMyMovement();
                    f1 = this.level().getBlockState(groundPos).getFriction(level(), groundPos, this) * 0.98F;
                }

                this.setDeltaMovement(this.getDeltaMovement().multiply((double)f1, 0.98D, (double)f1));
                if (this.onGround()) {
                    Vec3 vec31 = this.getDeltaMovement();
                    if (vec31.y < 0.0D) {
                        this.setDeltaMovement(vec31.multiply(1.0D, -0.5D, 1.0D));
                    }
                }
            }


            this.hasImpulse |= this.updateInWaterStateAndDoFluidPushing();
            if (!this.level().isClientSide) {
                double d0 = this.getDeltaMovement().subtract(vec3).lengthSqr();
                if (d0 > 0.01D) {
                    this.hasImpulse = true;
                }
            }

            ItemStack item = this.getItem();
            if (item.isEmpty() && !this.isRemoved()) {
                this.discard();
            }

        }
    }


    protected @NotNull BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void setUnderwaterMovement() {
        Vec3 vec3 = this.getDeltaMovement();
        this.setDeltaMovement(vec3.x * (double)0.99F, vec3.y + (double)(vec3.y < (double)0.06F ? 5.0E-4F : 0.0F), vec3.z * (double)0.99F);
    }

    private void setUnderLavaMovement() {
        Vec3 vec3 = this.getDeltaMovement();
        this.setDeltaMovement(vec3.x * (double)0.95F, vec3.y + (double)(vec3.y < (double)0.06F ? 5.0E-4F : 0.0F), vec3.z * (double)0.95F);
    }


    public ItemStack getItem() {
        return this.entityData.get(DATA_ITEM);
    }

    private void setItem(ItemStack item){
        this.entityData.set(DATA_ITEM, item);
    }

    public DropType getDropType() {
        return DropType.values()[this.entityData.get(DATA_TYPE)];
    }

    public void setDataType(DropType type) {
        this.entityData.set(DATA_TYPE, type.ordinal());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {
        this.setDataType(DropType.valueOf(pCompound.getString("DropType")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {
        pCompound.putString("DropType",this.getDropType().toString());
    }

    public void playerTouch(@NotNull Player pEntity) {
        if (!this.level().isClientSide) {
            if(this.pickupDelay == 0 && this.getDropType().playerPredicate.test(pEntity)){
                ItemStack itemStack = this.getItem();
                if (!itemStack.isEmpty()) {
                    itemStack.shrink(1);
                    ItemStack copy = itemStack.copy();
                    copy.setCount(1);
                    BaseMap map = FPSMCore.getInstance().getMapByPlayer(pEntity);
                    if (map instanceof ShopMap<?> shopMap) {
                        map.getMapTeams().getTeamByPlayer(pEntity).ifPresent(team->{
                            FPSMShop shop = shopMap.getShop(team.name);
                            if (shop == null) return;

                            ShopData shopData = shop.getPlayerShopData(pEntity.getUUID());
                            Pair<ItemType, ShopSlot> pair = shopData.checkItemStackIsInData(copy);
                            if(pair != null){
                                ShopSlot slot = pair.getSecond();
                                slot.lock(copy.getCount());
                                shop.syncShopData((ServerPlayer) pEntity,pair.getFirst(),slot);
                            }
                        });
                    }
                    pEntity.addItem(copy);
                    FPSMUtil.sortPlayerInventory(pEntity);
                    pEntity.level().playSound(pEntity,getOnPos(), SoundEvents.ITEM_PICKUP,pEntity.getSoundSource(),1,1);
                }else{
                    this.discard();
                }
            }
        }
    }

}
