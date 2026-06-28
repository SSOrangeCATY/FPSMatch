package com.tacz.guns.util.block;

import com.google.common.collect.Sets;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.util.HitboxHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.EventHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ProjectileExplosion extends ServerExplosion {
    private static final ExplosionDamageCalculator DEFAULT_CONTEXT = new ExplosionDamageCalculator();
    private final ServerLevel level;
    private final Vec3 center;
    private final double x;
    private final double y;
    private final double z;
    private final float power;
    private final float radius;
    private final boolean fire;
    private final boolean knockback;
    private final Entity owner;
    private final Entity exploder;
    private final ExplosionDamageCalculator damageCalculator;
    private final List<BlockPos> toBlow = new ArrayList<>();

    public ProjectileExplosion(ServerLevel level, Entity owner, Entity exploder, @Nullable DamageSource source, @Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float power, float radius, boolean knockback, Explosion.BlockInteraction mode) {
        super(level, exploder, source, damageCalculator, new Vec3(x, y, z), radius, AmmoConfig.EXPLOSIVE_AMMO_FIRE.get(), mode);
        this.level = level;
        this.center = new Vec3(x, y, z);
        this.x = x;
        this.y = y;
        this.z = z;
        this.power = power;
        this.radius = radius;
        this.fire = AmmoConfig.EXPLOSIVE_AMMO_FIRE.get();
        this.owner = owner;
        this.exploder = exploder;
        this.damageCalculator = damageCalculator == null ? DEFAULT_CONTEXT : damageCalculator;
        this.knockback = knockback;
    }

    @Override
    public int explode() {
        this.level.gameEvent(this.exploder, GameEvent.EXPLODE, this.center);
        Set<BlockPos> set = Sets.newHashSet();
        int i = 16;

        for (int x = 0; x < i; ++x) {
            for (int y = 0; y < i; ++y) {
                for (int z = 0; z < i; ++z) {
                    if (x == 0 || x == i - 1 || y == 0 || y == i - 1 || z == 0 || z == i - 1) {
                        double d0 = ((float) x / (i - 1) * 2.0F - 1.0F);
                        double d1 = ((float) y / (i - 1) * 2.0F - 1.0F);
                        double d2 = ((float) z / (i - 1) * 2.0F - 1.0F);
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                        d0 /= d3;
                        d1 /= d3;
                        d2 /= d3;
                        float f = this.radius * (0.7F + this.level.getRandom().nextFloat() * 0.6F);
                        double blockX = this.x;
                        double blockY = this.y;
                        double blockZ = this.z;

                        for (float f1 = 0.3F; f > 0.0F; f -= 0.22500001F) {
                            BlockPos pos = BlockPos.containing(blockX, blockY, blockZ);
                            BlockState blockState = this.level.getBlockState(pos);
                            FluidState fluidState = this.level.getFluidState(pos);
                            if (!this.level.isInWorldBounds(pos)) {
                                break;
                            }

                            Optional<Float> optional = this.damageCalculator.getBlockExplosionResistance(this, this.level, pos, blockState, fluidState);
                            if (optional.isPresent()) {
                                f -= (optional.get() + f1) * f1;
                            }

                            if (f > 0.0F && this.damageCalculator.shouldBlockExplode(this, this.level, pos, blockState, f)) {
                                set.add(pos);
                            }

                            blockX += d0 * (double) f1;
                            blockY += d1 * (double) f1;
                            blockZ += d2 * (double) f1;
                        }
                    }
                }
            }
        }

        this.toBlow.clear();
        this.toBlow.addAll(set);
        float radius = this.radius;
        int minX = Mth.floor(this.x - (double) radius - 1.0D);
        int maxX = Mth.floor(this.x + (double) radius + 1.0D);
        int minY = Mth.floor(this.y - (double) radius - 1.0D);
        int maxY = Mth.floor(this.y + (double) radius + 1.0D);
        int minZ = Mth.floor(this.z - (double) radius - 1.0D);
        int maxZ = Mth.floor(this.z + (double) radius + 1.0D);
        radius *= 2;
        List<Entity> entities = this.level.getEntities(this.exploder, new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        EventHooks.onExplosionDetonate(this.level, this, entities, this.toBlow);
        Vec3 explosionPos = new Vec3(this.x, this.y, this.z);

        for (Entity entity : entities) {
            if (entity.ignoreExplosion(this)) {
                continue;
            }

            AABB boundingBox = HitboxHelper.getFixedBoundingBox(entity, this.owner);
            BlockHitResult result;
            double strength;
            double deltaX;
            double deltaY;
            double deltaZ;
            double minDistance = radius;

            Vec3[] d = new Vec3[15];

            if (!(entity instanceof LivingEntity)) {
                strength = Math.sqrt(entity.distanceToSqr(explosionPos)) * 2 / radius;
                deltaX = entity.getX() - this.x;
                deltaY = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.y;
                deltaZ = entity.getZ() - this.z;
            } else {
                deltaX = (boundingBox.maxX + boundingBox.minX) / 2;
                deltaY = (boundingBox.maxY + boundingBox.minY) / 2;
                deltaZ = (boundingBox.maxZ + boundingBox.minZ) / 2;
                d[0] = new Vec3(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
                d[1] = new Vec3(boundingBox.minX, boundingBox.minY, boundingBox.maxZ);
                d[2] = new Vec3(boundingBox.minX, boundingBox.maxY, boundingBox.minZ);
                d[3] = new Vec3(boundingBox.maxX, boundingBox.minY, boundingBox.minZ);
                d[4] = new Vec3(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
                d[5] = new Vec3(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
                d[6] = new Vec3(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
                d[7] = new Vec3(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
                d[8] = new Vec3(boundingBox.minX, deltaY, deltaZ);
                d[9] = new Vec3(boundingBox.maxX, deltaY, deltaZ);
                d[10] = new Vec3(deltaX, boundingBox.minY, deltaZ);
                d[11] = new Vec3(deltaX, boundingBox.maxY, deltaZ);
                d[12] = new Vec3(deltaX, deltaY, boundingBox.minZ);
                d[13] = new Vec3(deltaX, deltaY, boundingBox.maxZ);
                d[14] = new Vec3(deltaX, deltaY, deltaZ);
                for (int s = 0; s < 15; s++) {
                    result = BlockRayTrace.rayTraceBlocks(this.level, new ClipContext(explosionPos, d[s], ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
                    minDistance = (result.getType() != BlockHitResult.Type.BLOCK) ? Math.min(minDistance, explosionPos.distanceTo(d[s])) : minDistance;
                }
                strength = minDistance * 2 / radius;
                deltaX -= this.x;
                deltaY -= this.y;
                deltaZ -= this.z;
            }

            if (strength > 1.0D) {
                continue;
            }

            double distanceToExplosion = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

            if (distanceToExplosion != 0.0D) {
                deltaX /= distanceToExplosion;
                deltaY /= distanceToExplosion;
                deltaZ /= distanceToExplosion;
            }

            double damage = 1.0D - strength;
            entity.hurtServer(this.level, this.getDamageSource(), (float) damage * this.power);

            if (entity instanceof LivingEntity livingEntity) {
                double knockbackResistance = livingEntity.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
                damage *= 1.0D - knockbackResistance;
            }

            float multiplier = this.power * radius / 500;
            // 启用击退效果
            if (AmmoConfig.EXPLOSIVE_AMMO_KNOCK_BACK.get() && this.knockback) {
                Vec3 knockback = new Vec3(deltaX * damage * multiplier, deltaY * damage * multiplier, deltaZ * damage * multiplier);
                knockback = EventHooks.getExplosionKnockback(this.level, this, entity, knockback, this.toBlow);
                entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
                if (entity instanceof Player player) {
                    if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
                        this.getHitPlayers().put(player, knockback);
                    }
                }
            }
        }

        if (this.interactsWithBlocks()) {
            this.interactWithBlocks(this.toBlow);
        }

        if (this.fire) {
            this.createFire(this.toBlow);
        }

        return this.toBlow.size();
    }

    public List<BlockPos> getToBlow() {
        return this.toBlow;
    }

    public void clearToBlow() {
        this.toBlow.clear();
    }

    private boolean interactsWithBlocks() {
        return this.getBlockInteraction() != Explosion.BlockInteraction.KEEP;
    }

    private void interactWithBlocks(List<BlockPos> targetBlocks) {
        List<StackCollector> stacks = new ArrayList<>();
        Util.shuffle(targetBlocks, this.level.getRandom());

        for (BlockPos pos : targetBlocks) {
            this.level.getBlockState(pos).onExplosionHit(this.level, pos, this, (stack, position) -> addOrAppendStack(stacks, stack, position));
        }

        for (StackCollector stack : stacks) {
            Block.popResource(this.level, stack.pos, stack.stack);
        }
    }

    private void createFire(List<BlockPos> targetBlocks) {
        for (BlockPos pos : targetBlocks) {
            if (this.level.getRandom().nextInt(3) == 0 && this.level.getBlockState(pos).isAir() && this.level.getBlockState(pos.below()).isSolidRender()) {
                this.level.setBlockAndUpdate(pos, BaseFireBlock.getState(this.level, pos));
            }
        }
    }

    private static void addOrAppendStack(List<StackCollector> stacks, ItemStack stack, BlockPos pos) {
        for (StackCollector stackCollector : stacks) {
            stackCollector.tryMerge(stack);
            if (stack.isEmpty()) {
                return;
            }
        }

        stacks.add(new StackCollector(pos, stack));
    }

    private static class StackCollector {
        private final BlockPos pos;
        private ItemStack stack;

        private StackCollector(BlockPos pos, ItemStack stack) {
            this.pos = pos;
            this.stack = stack;
        }

        public void tryMerge(ItemStack input) {
            if (ItemEntity.areMergable(this.stack, input)) {
                this.stack = ItemEntity.merge(this.stack, input, 16);
            }
        }
    }
}
