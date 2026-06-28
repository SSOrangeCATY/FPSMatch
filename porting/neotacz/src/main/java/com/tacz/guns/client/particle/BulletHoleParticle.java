package com.tacz.guns.client.particle;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import com.tacz.guns.particles.BulletHoleOption;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

/**
 * Author: Forked from MrCrayfish, continued by Timeless devs
 */
public class BulletHoleParticle extends SingleQuadParticle {
    private static final float BASE_ALPHA = 0.9F;
    private static final float LEGACY_DECAL_NORMAL_OFFSET = 0.01F;
    private static final float INV_SQRT_2 = 0.70710677F;
    private final Direction direction;
    private final BlockPos pos;
    private int uOffset;
    private int vOffset;
    private float textureDensity;
    private SingleQuadParticle.Layer layer = SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN;
    private float baseRed = 1.0F;
    private float baseGreen = 1.0F;
    private float baseBlue = 1.0F;

    public BulletHoleParticle(ClientLevel world, double x, double y, double z, Direction direction, BlockPos pos, String ammoId, String gunId, String gunDisplayId) {
        super(world, x, y, z, getSprite(world, pos));
        this.setSprite(getSprite(world, pos));
        this.direction = direction;
        this.pos = pos;
        this.lifetime = this.getLifetimeFromConfig(world);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.quadSize = 0.05F;

        BlockState state = world.getBlockState(pos);
        if (state.is(ModBlocks.TARGET.get()) || shouldRemove()) {
            this.remove();
        }
        TimelessAPI.getGunDisplay(Identifier.parse(gunDisplayId), Identifier.parse(gunId)).ifPresent(gunIndex -> {
            float[] gunTracerColor = gunIndex.getTracerColor();
            if (gunTracerColor != null) {
                this.setBaseColor(gunTracerColor[0], gunTracerColor[1], gunTracerColor[2]);
            } else {
                TimelessAPI.getClientAmmoIndex(Identifier.parse(ammoId)).ifPresent(ammoIndex -> {
                    float[] ammoTracerColor = ammoIndex.getTracerColor();
                    this.setBaseColor(ammoTracerColor[0], ammoTracerColor[1], ammoTracerColor[2]);
                });
            }
        });
        this.alpha = BASE_ALPHA;
    }

    private void setBaseColor(float red, float green, float blue) {
        this.baseRed = red;
        this.baseGreen = green;
        this.baseBlue = blue;
        this.rCol = red;
        this.gCol = green;
        this.bCol = blue;
    }

    private int getLifetimeFromConfig(ClientLevel world) {
        int configLife = RenderConfig.BULLET_HOLE_PARTICLE_LIFE.get();
        if (configLife <= 1) {
            return configLife;
        }
        return configLife + world.getRandom().nextInt(configLife / 2);
    }

    @Override
    protected void setSprite(TextureAtlasSprite sprite) {
        super.setSprite(sprite);
        this.uOffset = this.random.nextInt(16);
        this.vOffset = this.random.nextInt(16);
        // 材质应该都是方形
        this.textureDensity = (sprite.getU1() - sprite.getU0()) / 16.0F;
        this.layer = SingleQuadParticle.Layer.bySprite(sprite);
    }

    private static TextureAtlasSprite getSprite(ClientLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state, world, pos).sprite();
    }

    @Override
    protected float getU0() {
        return this.sprite.getU0() + this.uOffset * this.textureDensity;
    }

    @Override
    protected float getV0() {
        return this.sprite.getV0() + this.vOffset * this.textureDensity;
    }

    @Override
    protected float getU1() {
        return this.getU0() + this.textureDensity;
    }

    @Override
    protected float getV1() {
        return this.getV0() + this.textureDensity;
    }

    @Override
    public void tick() {
        super.tick();
        if (shouldRemove()) {
            this.remove();
        }
    }

    @Override
    public void extract(QuadParticleRenderState particleTypeRenderState, Camera camera, float partialTicks) {
        // 0 - 30 tick 内，从 15 亮度到 0 亮度
        int light = Math.max(15 - this.age / 2, 0);

        // 颜色，逐渐渐变到 0 0 0，也就是黑色
        float colorPercent = light / 15.0f;
        this.rCol = this.baseRed * colorPercent;
        this.gCol = this.baseGreen * colorPercent;
        this.bCol = this.baseBlue * colorPercent;

        // 透明度，逐渐变成 0，也就是透明
        double threshold = RenderConfig.BULLET_HOLE_PARTICLE_FADE_THRESHOLD.get() * this.lifetime;
        double fadeRange = this.lifetime - threshold;
        float fade = fadeRange <= 0 ? 1.0F : 1.0F - (float) (Math.max(this.age - threshold, 0) / fadeRange);
        this.alpha = BASE_ALPHA * Mth.clamp(fade, 0.0F, 1.0F);

        Vec3 view = camera.position();
        float particleX = (float) (Mth.lerp((double) partialTicks, this.xo, this.x) - view.x());
        float particleY = (float) (Mth.lerp((double) partialTicks, this.yo, this.y) - view.y());
        float particleZ = (float) (Mth.lerp((double) partialTicks, this.zo, this.z) - view.z());

        float normalOffset = this.getQuadSize(partialTicks) * LEGACY_DECAL_NORMAL_OFFSET;
        particleX += this.direction.getStepX() * normalOffset;
        particleY += this.direction.getStepY() * normalOffset;
        particleZ += this.direction.getStepZ() * normalOffset;

        // 1.20.1 手工提交的是 X/Z 平面、+Y 法线。26.1 retained quad 是 X/Y 平面、+Z 法线；
        // 这里先映射 (x, y, 0) -> (-x, 0, y)，再应用命中面朝向，保持旧 UV 方向和防 z-fight 偏移。
        Quaternionf legacyDecalBasis = new Quaternionf(0.0F, INV_SQRT_2, INV_SQRT_2, 0.0F);
        Quaternionf quaternion = new Quaternionf(this.direction.getRotation()).mul(legacyDecalBasis);
        this.extractRotatedQuad(particleTypeRenderState, quaternion, particleX, particleY, particleZ, partialTicks);
    }

    @Override
    protected int getLightCoords(float partialTicks) {
        int light = Math.max(15 - this.age / 2, 0);
        return LightCoordsUtil.pack(light, light);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return this.layer != null ? this.layer : SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN;
    }

    private boolean shouldRemove() {
        final BlockState blockState = this.level.getBlockState(this.pos);
        if (blockState.isAir()) {
            return true;
        } else {
            // 阻止弹孔在与方块不构成有效附着时继续渲染
            VoxelShape shape = blockState.getCollisionShape(this.level, this.pos);
            if (shape.isEmpty()) {
                return true;
            }
            AABB baseBlockBoundingBox = shape.bounds();
            AABB blockBoundingBox = baseBlockBoundingBox.move(this.pos);
            boolean intersects = blockBoundingBox.intersects(
                    this.x - 0.1, this.y - 0.1, this.z - 0.1,
                    this.x + 0.1, this.y + 0.1, this.z + 0.1);
            return !intersects;
        }
    }

    public static class Provider implements ParticleProvider<BulletHoleOption> {
        public Provider() {
        }

        @Override
        public Particle createParticle(@NotNull BulletHoleOption option, @NotNull ClientLevel world, double x, double y, double z, double pXSpeed, double pYSpeed, double pZSpeed, @NotNull RandomSource random) {
            return new BulletHoleParticle(world, x, y, z, option.getDirection(), option.getPos(), option.getAmmoId(), option.getGunId(), option.getGunDisplayId());
        }
    }
}
