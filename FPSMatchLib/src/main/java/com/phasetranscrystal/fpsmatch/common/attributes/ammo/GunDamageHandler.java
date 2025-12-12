package com.phasetranscrystal.fpsmatch.common.attributes.ammo;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.attribute.BulletproofArmorAttributeS2CPacket;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
    public class GunDamageHandler {
        private static final float BASE_ARMOR_PENETRATION = 1.4F;
        private static final float HEADSHOT_MULTIPLIER = 4.0F;

        @SubscribeEvent
        public static void onEntityHurtByGun(EntityHurtByGunEvent.Pre event) {
            if (!(event.getHurtEntity() instanceof ServerPlayer hurtEntity)) {
                return;
            }

            float baseDamage = event.getBaseAmount();
            boolean headshot = event.isHeadShot();
            if (headshot) {
                event.setHeadshotMultiplier(HEADSHOT_MULTIPLIER);
            }

            float armorValue = getArmorValue(hurtEntity,headshot);
            if (armorValue > 0) {
                float finalDamage = baseDamage * (BASE_ARMOR_PENETRATION / 2.0F);
                event.setBaseAmount(finalDamage);
                if(finalDamage > hurtEntity.getHealth()) {
                    BulletproofArmorAttribute.removePlayer(hurtEntity);
                }else{
                    int durabilityReduction = (int) Math.ceil(finalDamage);
                    reduceArmorDurability(hurtEntity, durabilityReduction);
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onPlayerDeath(LivingDeathEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer dead)) {
                return;
            }
            BulletproofArmorAttribute.removePlayer(dead);
        }

        @SubscribeEvent()
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            BulletproofArmorAttribute.getInstance(player)
                    .ifPresent(attribute -> FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new BulletproofArmorAttributeS2CPacket(attribute)));
        }

    public static int getArmorValue(Player player, boolean headshot) {
            Optional<BulletproofArmorAttribute> optional = BulletproofArmorAttribute.getInstance(player);
            if(optional.isPresent()) {
                BulletproofArmorAttribute attribute = optional.get();
                if(headshot) {
                    if(!attribute.hasHelmet()) {
                        return 0;
                    }else{
                        return attribute.getDurability();
                    }
                }else{
                    return attribute.getDurability();
                }
            }
            return 0;
        }

        public static void reduceArmorDurability(ServerPlayer player, int damage) {
            Optional<BulletproofArmorAttribute> optional = BulletproofArmorAttribute.getInstance(player);
            if(optional.isPresent()) {
                BulletproofArmorAttribute attribute = optional.get();
                if (attribute.getDurability() <= damage) {
                    BulletproofArmorAttribute.removePlayer(player);
                }else{
                    attribute.reduceDurability(damage);
                    FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new BulletproofArmorAttributeS2CPacket(attribute));
                }
            }
        }
    }