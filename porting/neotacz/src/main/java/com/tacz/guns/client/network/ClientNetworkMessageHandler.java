package com.tacz.guns.client.network;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.event.SwapItemWithOffHand;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunDrawEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunFireSelectEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import com.tacz.guns.network.message.ServerMessageCraft;
import com.tacz.guns.network.message.ServerMessageLevelUp;
import com.tacz.guns.network.message.ServerMessageRefreshRefitScreen;
import com.tacz.guns.network.message.ServerMessageSound;
import com.tacz.guns.network.message.ServerMessageSwapItem;
import com.tacz.guns.network.message.ServerMessageSyncBaseTimestamp;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.network.message.ServerMessageUpdateEntityData;
import com.tacz.guns.network.message.event.ServerMessageGunDraw;
import com.tacz.guns.network.message.event.ServerMessageGunFire;
import com.tacz.guns.network.message.event.ServerMessageGunFireSelect;
import com.tacz.guns.network.message.event.ServerMessageGunHurt;
import com.tacz.guns.network.message.event.ServerMessageGunKill;
import com.tacz.guns.network.message.event.ServerMessageGunMelee;
import com.tacz.guns.network.message.event.ServerMessageGunReload;
import com.tacz.guns.network.message.event.ServerMessageGunShoot;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.util.MinecraftGuiCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public final class ClientNetworkMessageHandler {
    private static final Marker SYNC_BASE_TIMESTAMP = MarkerManager.getMarker("SYNC_BASE_TIMESTAMP");

    private ClientNetworkMessageHandler() {
    }

    public static void handle(Object message) {
        if (message instanceof ServerMessageSound sound) {
            SoundPlayManager.playMessageSound(sound);
        } else if (message instanceof ServerMessageCraft craft) {
            handleCraft(craft);
        } else if (message instanceof ServerMessageRefreshRefitScreen refreshRefitScreen) {
            handleRefreshRefitScreen(refreshRefitScreen);
        } else if (message instanceof ServerMessageSwapItem) {
            NeoForge.EVENT_BUS.post(new SwapItemWithOffHand());
        } else if (message instanceof ServerMessageLevelUp levelUp) {
            handleLevelUp(levelUp);
        } else if (message instanceof ServerMessageUpdateEntityData updateEntityData) {
            handleUpdateEntityData(updateEntityData);
        } else if (message instanceof ServerMessageSyncGunPack syncGunPack) {
            handleSyncGunPack(syncGunPack);
        } else if (message instanceof ServerMessageSyncBaseTimestamp syncBaseTimestamp) {
            handleSyncBaseTimestamp(syncBaseTimestamp);
        } else if (message instanceof ServerMessageGunDraw gunDraw) {
            handleGunDraw(gunDraw);
        } else if (message instanceof ServerMessageGunFire gunFire) {
            handleGunFire(gunFire);
        } else if (message instanceof ServerMessageGunFireSelect gunFireSelect) {
            handleGunFireSelect(gunFireSelect);
        } else if (message instanceof ServerMessageGunMelee gunMelee) {
            handleGunMelee(gunMelee);
        } else if (message instanceof ServerMessageGunReload gunReload) {
            handleGunReload(gunReload);
        } else if (message instanceof ServerMessageGunShoot gunShoot) {
            handleGunShoot(gunShoot);
        } else if (message instanceof ServerMessageGunHurt gunHurt) {
            handleGunHurt(gunHurt);
        } else if (message instanceof ServerMessageGunKill gunKill) {
            handleGunKill(gunKill);
        } else {
            throw new IllegalArgumentException("Unknown TACZ clientbound message " + message.getClass().getName());
        }
    }

    private static void handleCraft(ServerMessageCraft message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu.containerId == message.getMenuId() && MinecraftGuiCompat.screen() instanceof GunSmithTableScreen screen) {
            screen.updateIngredientCount();
        }
    }

    private static void handleRefreshRefitScreen(ServerMessageRefreshRefitScreen message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && MinecraftGuiCompat.screen() instanceof GunRefitScreen screen) {
            screen.init();
            // Refit data is client-local; keep this side effect behind the client-only dispatcher.
            AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem());
        }
    }

    private static void handleLevelUp(ServerMessageLevelUp message) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        // Level-up toast behavior was already disabled in the original handler.
    }

    private static void handleUpdateEntityData(ServerMessageUpdateEntityData message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(message.getEntityId());
        if (entity == null) {
            return;
        }
        SyncedEntityData instance = SyncedEntityData.instance();
        message.getEntries().forEach(entry -> instance.set(entity, entry.getKey(), entry.getValue()));
    }

    private static void handleSyncGunPack(ServerMessageSyncGunPack message) {
        var networkCache = message.getCache();
        com.tacz.guns.GunMod.LOGGER.info("TACZ received gun pack sync: remote={} types={} recipes={} blockIndex={} gunIndex={} ammoIndex={} attachmentIndex={}",
                message.isRemoteConnection(), networkCache.size(),
                countNetworkCache(networkCache, DataType.RECIPES),
                countNetworkCache(networkCache, DataType.BLOCK_INDEX),
                countNetworkCache(networkCache, DataType.GUN_INDEX),
                countNetworkCache(networkCache, DataType.AMMO_INDEX),
                countNetworkCache(networkCache, DataType.ATTACHMENT_INDEX));
        if (message.isRemoteConnection()) {
            CommonAssetsManager.clearInstance();
        }
        CommonNetworkCache.INSTANCE.fromNetwork(message.getCache());
        com.tacz.guns.GunMod.LOGGER.info("TACZ CommonNetworkCache loaded: recipes={} blockIndex={} gunIndex={} ammoIndex={} attachmentIndex={}",
                CommonNetworkCache.INSTANCE.getAllRecipes().size(),
                CommonNetworkCache.INSTANCE.getAllBlocks().size(),
                CommonNetworkCache.INSTANCE.getAllGuns().size(),
                CommonNetworkCache.INSTANCE.getAllAmmos().size(),
                CommonNetworkCache.INSTANCE.getAllAttachments().size());
        ClientIndexManager.reload();
        if (MinecraftGuiCompat.screen() instanceof GunSmithTableScreen screen) {
            screen.refreshRecipesFromClientData();
        }
    }

    private static int countNetworkCache(java.util.Map<?, ? extends java.util.Map<?, ?>> cache, Object type) {
        java.util.Map<?, ?> values = cache.get(type);
        return values == null ? 0 : values.size();
    }

    private static void handleSyncBaseTimestamp(ServerMessageSyncBaseTimestamp message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        LocalPlayerDataHolder dataHolder = IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder();
        dataHolder.clientBaseTimestamp = message.getClientReceiveTimestamp();
        GunMod.LOGGER.debug(SYNC_BASE_TIMESTAMP, "Update client base timestamp: {}", dataHolder.clientBaseTimestamp);
    }

    private static void handleGunDraw(ServerMessageGunDraw message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level.getEntity(message.getEntityId()) instanceof LivingEntity livingEntity) {
            NeoForge.EVENT_BUS.post(new GunDrawEvent(livingEntity, message.getPreviousGunItem(), message.getCurrentGunItem(), LogicalSide.CLIENT));
        }
    }

    private static void handleGunFire(ServerMessageGunFire message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level.getEntity(message.getShooterId()) instanceof LivingEntity shooter) {
            NeoForge.EVENT_BUS.post(new GunFireEvent(shooter, message.getGunItemStack(), LogicalSide.CLIENT));
        }
    }

    private static void handleGunFireSelect(ServerMessageGunFireSelect message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level.getEntity(message.getShooterId()) instanceof LivingEntity shooter) {
            NeoForge.EVENT_BUS.post(new GunFireSelectEvent(shooter, message.getGunItemStack(), LogicalSide.CLIENT));
        }
    }

    private static void handleGunMelee(ServerMessageGunMelee message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level.getEntity(message.getShooterId()) instanceof LivingEntity shooter) {
            NeoForge.EVENT_BUS.post(new GunMeleeEvent(shooter, message.getGunItemStack(), LogicalSide.CLIENT));
        }
    }

    private static void handleGunReload(ServerMessageGunReload message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level.getEntity(message.getShooterId()) instanceof LivingEntity shooter) {
            NeoForge.EVENT_BUS.post(new GunReloadEvent(shooter, message.getGunItemStack(), LogicalSide.CLIENT));
        }
    }

    private static void handleGunShoot(ServerMessageGunShoot message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level.getEntity(message.getShooterId()) instanceof LivingEntity shooter) {
            NeoForge.EVENT_BUS.post(new GunShootEvent(shooter, message.getGunItemStack(), LogicalSide.CLIENT));
        }
    }

    private static void handleGunHurt(ServerMessageGunHurt message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity bullet = level.getEntity(message.getBulletId());
        Entity hurtEntity = level.getEntity(message.getHurtEntityId());
        LivingEntity attacker = level.getEntity(message.getAttackerId()) instanceof LivingEntity livingEntity ? livingEntity : null;
        NeoForge.EVENT_BUS.post(new EntityHurtByGunEvent.Post(bullet, hurtEntity, attacker, message.getGunId(), message.getGunDisplayId(),
                message.getAmount(), null, message.isHeadShot(), message.getHeadshotMultiplier(), LogicalSide.CLIENT));
    }

    private static void handleGunKill(ServerMessageGunKill message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity bullet = level.getEntity(message.getBulletId());
        LivingEntity killedEntity = level.getEntity(message.getKillEntityId()) instanceof LivingEntity livingEntity ? livingEntity : null;
        LivingEntity attacker = level.getEntity(message.getAttackerId()) instanceof LivingEntity livingEntity ? livingEntity : null;
        NeoForge.EVENT_BUS.post(new EntityKillByGunEvent(bullet, killedEntity, attacker, message.getGunId(), message.getGunDisplayId(),
                message.getBaseDamage(), null, message.isHeadShot(), message.getHeadshotMultiplier(), LogicalSide.CLIENT));
    }
}
