package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.extensions.IForgeItem;

public class MatchDropEntity extends Entity {
    //TODO
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(MatchDropEntity.class, EntityDataSerializers.ITEM_STACK);
    public MatchDropEntity(EntityType<? extends ItemEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }


    @Override
    protected void defineSynchedData() {

    }

    @Override
    public void tick() {
        if (this.getItem().isEmpty()) {
            this.discard();
        } else {
            super.baseTick();
            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            if (!this.isNoGravity()) {
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

                this.setDeltaMovement(this.getDeltaMovement().multiply(f1, 0.98D, f1));
                if (this.onGround()) {
                    Vec3 vec31 = this.getDeltaMovement();
                    if (vec31.y < 0.0D) {
                        this.setDeltaMovement(vec31.multiply(1.0D, -0.5D, 1.0D));
                    }
                }
            }

            ItemStack item = this.getItem();
            if (item.isEmpty() && !this.isRemoved()) {
                this.discard();
            }

        }
    }

    private ItemStack getItem() {
        return null;
    }

    @Override
    public boolean isPickable() {
        // 重写 isPickable 方法，确保玩家需要进行交互才能捡起来
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {

    }

    public void playerTouch(Player pEntity) {
        if (!this.level().isClientSide) {
            //TODO 可以设置相应的test来调用pickUPItem方法什么的
        }
    }

}
