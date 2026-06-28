package com.tacz.guns.util;

import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.util.block.ProjectileExplosion;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Optional;

public class ExplodeUtil {
    public static void createExplosion(Entity owner, Entity exploder, float damage, float radius, boolean knockback, boolean destroy, Vec3 hitPos) {
        // 客户端不执行
        if (!(exploder.level() instanceof ServerLevel level)) {
            return;
        }
        // 依据配置文件读取方块破坏方式
        Explosion.BlockInteraction mode = Explosion.BlockInteraction.KEEP;
        if (destroy) {
            mode = Explosion.BlockInteraction.DESTROY;
        }
        // 创建爆炸
        ProjectileExplosion explosion = new ProjectileExplosion(level, owner, exploder, null, null, hitPos.x(), hitPos.y(), hitPos.z(), damage, radius, knockback, mode);
        // 监听 forge 事件
        if (EventHooks.onExplosionStart(level, explosion)) {
            return;
        }
        // 执行爆炸逻辑
        explosion.explode();
        if (mode == Explosion.BlockInteraction.KEEP) {
            explosion.clearToBlow();
        }
        // 客户端发包，发送爆炸相关信息
        level.players().stream().filter(player -> Mth.sqrt((float) player.distanceToSqr(hitPos)) < AmmoConfig.EXPLOSIVE_AMMO_VISIBLE_DISTANCE.get()).forEach(player -> {
            ClientboundExplodePacket packet = new ClientboundExplodePacket(
                    hitPos,
                    radius,
                    explosion.getToBlow().size(),
                    Optional.ofNullable(explosion.getHitPlayers().get(player)),
                    radius >= 2.0F ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION,
                    SoundEvents.GENERIC_EXPLODE,
                    WeightedList.of(new ExplosionParticleInfo(ParticleTypes.POOF, 1.0F, 1.0F))
            );
            player.connection.send(packet);
        });
    }
}
