package com.tacz.guns.client.init;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.particle.BulletHoleParticle;
import com.tacz.guns.init.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = GunMod.MOD_ID, value = Dist.CLIENT)
public class ParticleFactoryRegistry {
    @SubscribeEvent
    public static void onRegisterParticleFactory(RegisterParticleProvidersEvent event) {
        event.registerSpecial(ModParticles.BULLET_HOLE.get(), new BulletHoleParticle.Provider());
    }
}
