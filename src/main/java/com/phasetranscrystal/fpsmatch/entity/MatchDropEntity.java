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
        if (this.getItem() != null && this.getItem().isEmpty()) {
            this.discard();
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
