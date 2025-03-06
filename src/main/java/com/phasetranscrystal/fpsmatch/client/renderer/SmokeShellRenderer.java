package com.phasetranscrystal.fpsmatch.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.phasetranscrystal.fpsmatch.entity.SmokeShellEntity;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SmokeShellRenderer implements EntityRendererProvider<SmokeShellEntity> {

    @Override
    public @NotNull EntityRenderer<SmokeShellEntity> create(@NotNull Context context) {
        return new EntityRenderer<>(context) {
            List<Particle> particleList = new ArrayList<>();
            final ItemEntityRenderer itemRender = new ItemEntityRenderer(context);
            ItemEntity item = null;
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
        };
    }
}