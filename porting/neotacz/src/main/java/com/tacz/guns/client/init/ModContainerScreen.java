package com.tacz.guns.client.init;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public class ModContainerScreen {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(GunSmithTableMenu.TYPE, GunSmithTableScreen::new);
    }
}
