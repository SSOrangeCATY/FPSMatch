package com.phasetranscrystal.fpsmatch.common.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.phasetranscrystal.fpsmatch.common.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.common.client.screen.CSGameShopScreen;
import icyllis.modernui.mc.forge.MuiForgeApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class OpenShopKey {
    public static final KeyMapping OPEN_SHOP_KEY = new KeyMapping("key.fpsm.open.shop.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.category.fpsm");

    @SubscribeEvent
    public static void onInspectPress(InputEvent.Key event) {
        if (isInGame() && event.getAction() == GLFW.GLFW_PRESS && OPEN_SHOP_KEY.matches(event.getKey(), event.getScanCode())) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isSpectator()) {
                return;
            }

            if(ClientData.isDebug){
                MuiForgeApi.openScreen(CSGameShopScreen.getInstance());
            }else{
                if(ClientData.currentMap.equals("fpsm_none")){
                    Minecraft.getInstance().player.sendSystemMessage(Component.translatable("key.fpsm.open.shop.failed.no_map"));
                    return;
                }

                if(!ClientData.currentMapSupportShop){
                    Minecraft.getInstance().player.sendSystemMessage(Component.translatable("key.fpsm.open.shop.failed.game_type.no_shop"));
                    return;
                }

                if(!ClientData.canOpenShop){
                    Minecraft.getInstance().player.sendSystemMessage(Component.translatable("key.fpsm.open.shop.failed.purchase_time.expired"));
                    return;
                }

                if(ClientData.isStart){
                    MuiForgeApi.openScreen(CSGameShopScreen.getInstance());
                }else{
                    Minecraft.getInstance().player.sendSystemMessage(Component.translatable("key.fpsm.open.shop.failed.game.not_started"));
                }
            }
        }
    }
}