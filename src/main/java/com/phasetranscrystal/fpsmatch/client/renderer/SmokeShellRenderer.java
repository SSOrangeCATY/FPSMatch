package com.phasetranscrystal.fpsmatch.client.renderer;

import com.mojang.blaze3d.vertex.*;
import com.phasetranscrystal.fpsmatch.entity.SmokeShellEntity;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SmokeShellRenderer implements EntityRendererProvider<SmokeShellEntity> {

    // 配置参数
    private static final int LAYERS = 24; // 增加层数
    private static final float MAX_SIZE = 5.0f; // 适当减小基础尺寸
    private static final Vector3f SMOKE_COLOR = new Vector3f(0.4f, 0.4f, 0.4f);
    private static final float BASE_SIZE = 5.0f;  // 基础尺寸
    private static final ResourceLocation SMOKE_TEXTURE = InventoryMenu.BLOCK_ATLAS;
    // 优化后的渲染类型
    private static final RenderType SMOKE_RENDER_TYPE = RenderType.entityTranslucent(SMOKE_TEXTURE);


    @Override
    public @NotNull EntityRenderer<SmokeShellEntity> create(@NotNull Context context) {
        return new EntityRenderer<>(context) {
            List<Particle> particleList = new ArrayList<>();
            final ItemEntityRenderer itemRender = new ItemEntityRenderer(context);
            ItemEntity item = null;

            /*
            // 随机数生成器
            private final Random random = new Random(42);
            // 预生成旋转偏移量
            private final Vector3f[] offsets = new Vector3f[LAYERS];
            private final float[] rotations = new float[LAYERS];

            {
                // 初始化三维偏移和随机旋转
                for (int i = 0; i < LAYERS; i++) {
                    // 球面坐标生成
                    double theta = random.nextDouble() * Math.PI;
                    double phi = random.nextDouble() * 2 * Math.PI;
                    double radius = 0.5 + random.nextDouble();

                    offsets[i] = new Vector3f(
                            (float)(radius * Math.sin(theta) * Math.cos(phi)),
                            (float)(radius * Math.sin(theta) * Math.sin(phi)),
                            (float)(radius * Math.cos(theta))
                    );
                    rotations[i] = random.nextFloat() * 360;
                }
            }*/
            @Override
            public @NotNull ResourceLocation getTextureLocation(@NotNull SmokeShellEntity entity) {
                return InventoryMenu.BLOCK_ATLAS;
            }

            @Override
            public void render(@NotNull SmokeShellEntity entity, float yaw, float partialTicks,
                               @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
                if(entity.isActivated()){
                   if(entity.getParticleCoolDown() == 0){
                        ClientLevel level = Minecraft.getInstance().level;
                        this.spawnSmokeLayer(entity,new Random(),level.getBlockState(entity.blockPosition().below()).isAir());
                    }else{
                        this.removeAllParticle();
                    }
                }else{
                    if (item == null) {
                        item = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), new ItemStack(FPSMItemRegister.SMOKE_SHELL.get()));
                    }
                    itemRender.render(item, 0, 0, poseStack, buffer, packedLight);
                }
            }
            private void spawnSmokeLayer(SmokeShellEntity entity, Random random, boolean hasFloor) {
                int yd_ = hasFloor ? -1 : 1;
                int r = 4;
                double x = entity.getX();
                double y = entity.getY();
                double z = entity.getZ();

                double theta = random.nextDouble() * 2 * Math.PI;
                double phi = Math.acos(2 * random.nextDouble() - 1);
                double radius = r * Math.sqrt(random.nextDouble());

                double xOffset = radius * Math.sin(phi) * Math.cos(theta);
                double yOffset = radius * Math.cos(phi) * yd_;
                double zOffset = radius * Math.sin(phi) * Math.sin(theta);

                Particle p = Minecraft.getInstance().particleEngine.createParticle(
                        entity.getParticleOptions(),
                        x + xOffset + random.nextDouble(-0.2, 0.2),
                        y + yOffset + random.nextDouble(-0.1, 0.1),
                        z + zOffset + random.nextDouble(-0.2, 0.2),
                        0, 0, 0
                );
                this.addParticle(p);
            }

            private void addParticle(Particle particle){
                this.particleList.add(particle);
                Minecraft.getInstance().particleEngine.add(particle);
            }

            private void removeAllParticle(){
                if(this.particleList.isEmpty()) return;
                this.particleList.forEach(Particle::remove);
                this.particleList.clear();
            }
/*

            private void centerToEntity(SmokeShellEntity entity, float partialTicks, PoseStack poseStack) {
                Vec3 pos = entity.getPosition(partialTicks);
                poseStack.translate(pos.x, pos.y + 0.8, pos.z);
            }

            private void applyLayerTransform(PoseStack poseStack, int layerIndex, float progress) {
                // 动态参数
                float floatOffset = Mth.sin(progress * 3 + layerIndex) * 0.5f;
                float scaleGrowth = 1 + progress * 0.8f;

                // 应用变换
                Vector3f offset = offsets[layerIndex];
                poseStack.translate(
                        offset.x() * scaleGrowth,
                        offset.y() * scaleGrowth + floatOffset,
                        offset.z() * scaleGrowth
                );
                poseStack.mulPose(Axis.YP.rotationDegrees(rotations[layerIndex] + progress * 120));
                poseStack.scale(scaleGrowth, scaleGrowth, scaleGrowth);
            }

            private void renderSmokeLayer(Matrix4f matrix, Matrix3f normal, VertexConsumer consumer,
                                          float size, float alpha) {
                float halfSize = size / 2;
                Vector3f[] quadVertices = {
                        new Vector3f(-halfSize, -halfSize, 0),
                        new Vector3f(halfSize, -halfSize, 0),
                        new Vector3f(halfSize, halfSize, 0),
                        new Vector3f(-halfSize, halfSize, 0)
                };

                // 四个不同朝向的四边形
                for (Axis axis : new Axis[]{Axis.XP, Axis.YP, Axis.ZP}) {
                    Quaternionf rotation = axis.rotationDegrees(90);
                    buildRotatedQuad(matrix, normal, consumer, quadVertices, rotation, alpha);
                }
            }

            private void buildRotatedQuad(Matrix4f matrix, Matrix3f normal, VertexConsumer consumer,
                                          Vector3f[] vertices, Quaternionf rotation, float alpha) {
                Vector3f normalVec = new Vector3f(0, 0, 1).rotate(rotation);

                for (Vector3f vert : vertices) {
                    Vector3f rotatedVert = vert.rotate(rotation);
                    consumer.vertex(matrix, rotatedVert.x, rotatedVert.y, rotatedVert.z)
                            .color(SMOKE_COLOR.x(), SMOKE_COLOR.y(), SMOKE_COLOR.z(), alpha)
                            .uv(getU(rotatedVert), getV(rotatedVert))
                            .overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(15728880)
                            .normal(normal, normalVec.x(), normalVec.y(), normalVec.z())
                            .endVertex();
                }
            }

            private float getU(Vector3f vert) {
                return (vert.x() + 1) / 2;
            }

            private float getV(Vector3f vert) {
                return (vert.y() + 1) / 2;
            }
        };*/
        };
    }
}