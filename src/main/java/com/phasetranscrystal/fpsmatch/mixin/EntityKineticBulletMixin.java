package com.phasetranscrystal.fpsmatch.mixin;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class EntityKineticBulletMixin extends Projectile {
    // 新增字段：穿透次数和伤害倍率
    @Unique
    private float fPSMatch$damageMultiplier = 1.0f;

    // Mixin 构造函数（必须存在，但可为空）
    protected EntityKineticBulletMixin(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    // --- 修改 onHitBlock 方法 ---
    @Inject(
            method = "onHitBlock",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onHitBlockProxy(BlockHitResult result, Vec3 startVec, Vec3 endVec, CallbackInfo ci) {
        BlockState blockState = this.level().getBlockState(result.getBlockPos());
        ResourceLocation penetrableTag = new ResourceLocation("minecraft:penetrable_blocks");
        TagKey<Block> penetrableTagKey = TagKey.create(Registries.BLOCK, penetrableTag);
        BlockPos pos = result.getBlockPos();
        // 判断是否为可穿透方块
        if (blockState.is(penetrableTagKey)) {
            if (!MinecraftForge.EVENT_BUS.post(new AmmoHitBlockEvent(this.level(), result, this.level().getBlockState(pos), (EntityKineticBullet)(Object)this))) {
                fPSMatch$damageMultiplier *= 0.8f;

                // 生成穿透特效（服务端）
                if (!this.level().isClientSide()) {
                    ServerLevel serverLevel = (ServerLevel) this.level();
                    serverLevel.sendParticles(
                            ParticleTypes.CRIT,
                            result.getLocation().x,
                            result.getLocation().y,
                            result.getLocation().z,
                            5, 0, 0, 0, 0.1
                    );
                }

                // 取消原逻辑（阻止销毁子弹）
                ci.cancel();
            }
        }
    }

    // --- 修改伤害计算逻辑 ---
    @Inject(
            method = "getDamage",
            at = @At(value = "RETURN"),
            remap = false, cancellable = true)
    private void applyDamageMultiplier(Vec3 hitVec, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() * this.fPSMatch$damageMultiplier);
    }

    // --- 同步新增字段 ---
    @Inject(method = "writeSpawnData", at = @At("HEAD"))
    private void writeAdditionalData(FriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeFloat(this.fPSMatch$damageMultiplier);
    }

    @Inject(method = "readSpawnData", at = @At("HEAD"))
    private void readAdditionalData(FriendlyByteBuf additionalData, CallbackInfo ci) {
        this.fPSMatch$damageMultiplier = additionalData.readFloat();
    }
}