package com.tacz.guns.entity;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.ITargetEntity;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.init.ModSounds;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunHurt;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.LogicalSide;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TargetMinecart extends AbstractMinecart implements ITargetEntity {
    private static final ResourceKey<EntityType<?>> TARGET_MINECART_TYPE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "target_minecart"));
    public static EntityType<TargetMinecart> TYPE = EntityType.Builder.<TargetMinecart>of(TargetMinecart::new, MobCategory.MISC)
            .sized(0.75F, 2.4F)
            .clientTrackingRange(8)
            .build(TARGET_MINECART_TYPE_KEY);

    private @Nullable GameProfile gameProfile = null;

    public TargetMinecart(EntityType<TargetMinecart> type, Level world) {
        super(type, world);
    }

    public TargetMinecart(Level level, double x, double y, double z) {
        super(TYPE, level, x, y, z);
    }

    @Override
    public void onProjectileHit(Entity entity, EntityHitResult result, DamageSource source, float damage) {
        if (this.level().isClientSide() || this.isRemoved()) {
            return;
        }
        if (source.isDirect()) {
            return;
        }
        Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof Player player) {
            this.setHurtDir(-1);
            this.setHurtTime(10);
            this.markHurt();
            this.setDamage(10);
            double dis = this.position().distanceTo(sourceEntity.position());
            player.sendOverlayMessage(Component.translatable("message.tacz.target_minecart.hit", String.format("%.1f", damage), String.format("%.2f", dis)));
            // 原版的声音传播距离由 volume 决定
            // 当声音大于 1 时，距离为 = 16 * volume
            float volume = OtherConfig.TARGET_SOUND_DISTANCE.get() / 16.0f;
            volume = Math.max(volume, 0);
            level().playSound(null, this, ModSounds.TARGET_HIT.get(), SoundSource.BLOCKS, volume, this.level().getRandom().nextFloat() * 0.1F + 0.9F);

            if (entity instanceof EntityKineticBullet projectile) {
                boolean isHeadshot = false;
                float headshotMultiplier = 1;
                NeoForge.EVENT_BUS.post(new EntityHurtByGunEvent.Post(projectile, this, player, projectile.getGunId(), projectile.getGunDisplayId(), damage, Pair.of(source, source), isHeadshot, headshotMultiplier, LogicalSide.SERVER));
                NetworkHandler.sendToDimension(new ServerMessageGunHurt(projectile.getId(), this.getId(), player.getId(), projectile.getGunId(), projectile.getGunDisplayId(), damage, isHeadshot, headshotMultiplier), this);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public boolean isRideable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double size = this.getBoundingBox().getSize();
        if (Double.isNaN(size)) {
            size = 1.0;
        }
        size *= RenderConfig.TARGET_RENDER_DISTANCE.get() * getViewScale();
        return distance < size * size;
    }

    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        this.kill(level);
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            ItemStack itemStack = new ItemStack(ModItems.TARGET_MINECART.get());
            if (this.hasCustomName()) {
                itemStack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
            }
            this.spawnAtLocation(level, itemStack);
        }
    }

    @Override
    protected Item getDropItem() {
        return ModItems.TARGET_MINECART.get();
    }

    @Override
    public ItemStack getPickResult() {
        ItemStack itemStack = new ItemStack(ModItems.TARGET_MINECART.get());
        if (this.hasCustomName()) {
            itemStack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        }
        return itemStack;
    }

    @Nullable
    public GameProfile getGameProfile() {
        if (this.gameProfile == null && this.getCustomName() != null) {
            String name = this.getCustomName().getString();
            this.gameProfile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        }
        return gameProfile;
    }

    @Override
    @NotNull
    public BlockState getDefaultDisplayBlockState() {
        return ModBlocks.TARGET.get().defaultBlockState();
    }

    @Override
    protected double getMaxSpeed(ServerLevel level) {
        return 0.2F;
    }
}
