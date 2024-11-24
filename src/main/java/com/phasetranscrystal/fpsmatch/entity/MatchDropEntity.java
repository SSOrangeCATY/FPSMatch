package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MatchDropEntity<T extends BaseMap> extends ItemEntity {
    //TODO
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(MatchDropEntity.class, EntityDataSerializers.ITEM_STACK);
    public MatchDropEntity(EntityType<? extends ItemEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    public MatchDropEntity(Level pLevel, double pPosX, double pPosY, double pPosZ, ItemStack pItemStack) {
        super(pLevel, pPosX, pPosY, pPosZ, pItemStack);
    }

    public MatchDropEntity(Level pLevel, double pPosX, double pPosY, double pPosZ, ItemStack pItemStack, double pDeltaX, double pDeltaY, double pDeltaZ) {
        super(pLevel, pPosX, pPosY, pPosZ, pItemStack, pDeltaX, pDeltaY, pDeltaZ);
    }


    @Override
    public void tick() {
        if (getItem().onEntityItemUpdate(this)) return;
        if (this.getItem().isEmpty()) {
            this.discard();
        } else {
            super.baseTick();
            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            Vec3 vec3 = this.getDeltaMovement();
            float f = this.getEyeHeight() - 0.11111111F;
            net.minecraftforge.fluids.FluidType fluidType = this.getMaxHeightFluidType();
            if (!fluidType.isAir() && !fluidType.isVanilla() && this.getFluidTypeHeight(fluidType) > (double)f) fluidType.setItemMovement(this);
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

                this.setDeltaMovement(this.getDeltaMovement().multiply((double)f1, 0.98D, (double)f1));
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

    @Override
    public boolean isPickable() {
        // 重写 isPickable 方法，确保玩家需要进行交互才能捡起来
        return false;
    }

    public void playerTouch(Player pEntity) {
        if (!this.level().isClientSide) {
            ItemStack itemstack = this.getItem();
            Item item = itemstack.getItem();
            int i = itemstack.getCount();
            int hook = net.minecraftforge.event.ForgeEventFactory.onItemPickup(this, pEntity);
            if (hook < 0) return;
            ItemStack copy = itemstack.copy();
            if (hook == 1 || i <= 0 || pEntity.getInventory().add(itemstack)) {
                i = copy.getCount() - itemstack.getCount();
                copy.setCount(i);
                net.minecraftforge.event.ForgeEventFactory.firePlayerItemPickupEvent(pEntity, this, copy);
                pEntity.take(this, i);
                if (itemstack.isEmpty()) {
                    this.discard();
                    itemstack.setCount(i);
                }

                pEntity.awardStat(Stats.ITEM_PICKED_UP.get(item), i);
                pEntity.onItemPickup(this);
            }
        }
    }

}
