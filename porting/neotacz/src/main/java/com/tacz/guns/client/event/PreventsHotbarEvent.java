package com.tacz.guns.client.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.util.MinecraftGuiCompat;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public class PreventsHotbarEvent {
    @SubscribeEvent
    public static void onRenderHotbarEvent(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }
        // todo 需要测试行为
        Screen screen = MinecraftGuiCompat.screen();
        // 枪械合成台界面关闭背景
        if (screen instanceof GunSmithTableScreen) {
            event.setCanceled(true);
            return;
        }
        // 枪械改装界面关闭背景
        if (screen instanceof GunRefitScreen) {
            event.setCanceled(true);
        }
    }
}
