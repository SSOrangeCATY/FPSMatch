package com.tacz.guns.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.init.ModAttributes;
import com.tacz.guns.init.ModDamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber
public class EntityDamageEvent {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingIncomingDamageEvent event){
        if (event.getSource().is(ModDamageTypes.BULLETS_TAG)) {
            LivingEntity living = event.getEntity();

            AttributeInstance resistance = living.getAttribute(ModAttributes.BULLET_RESISTANCE);
            if (resistance != null) {
                float modifiedDamage = event.getAmount() * (float) (1 - resistance.getValue());
                event.setAmount(modifiedDamage);
            }
        }
    }
}
