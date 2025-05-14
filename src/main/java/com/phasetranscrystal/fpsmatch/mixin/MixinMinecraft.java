package com.phasetranscrystal.fpsmatch.mixin;


import com.phasetranscrystal.fpsmatch.item.BaseThrowAbleItem;
import net.minecraft.client.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow @Nullable public LocalPlayer player;

    @Shadow @Final public Options options;

    @Shadow protected abstract void startUseItem();

    @Shadow private int rightClickDelay;

    @Shadow @Nullable public MultiPlayerGameMode gameMode;

    @Shadow @Final public LevelRenderer levelRenderer;

    @Shadow @Final public GameRenderer gameRenderer;

    @Shadow @Nullable public abstract Entity getCameraEntity();

    @Shadow @Final public Gui gui;

    @Shadow @Nullable public Screen screen;

    @Shadow protected abstract boolean isMultiplayerServer();

    @Shadow @Final private static Component SOCIAL_INTERACTIONS_NOT_AVAILABLE;

    @Shadow @Final private GameNarrator narrator;

    @Shadow @Nullable private TutorialToast socialInteractionsToast;

    @Shadow @Final private Tutorial tutorial;

    @Shadow public abstract void setScreen(@org.jetbrains.annotations.Nullable Screen pGuiScreen);

    @Shadow @Nullable public abstract ClientPacketListener getConnection();

    @Shadow protected abstract void openChatScreen(String pDefaultText);

    @Shadow @Nullable private Overlay overlay;

    @Shadow protected abstract boolean startAttack();

    @Shadow protected abstract void pickBlock();

    @Shadow protected abstract void continueAttack(boolean pLeftClick);

    @Shadow @Final public MouseHandler mouseHandler;

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void onHandleKeybinds(CallbackInfo ci) {
        if(this.player.getOffhandItem().getItem() instanceof BaseThrowAbleItem || this.player.getMainHandItem().getItem() instanceof BaseThrowAbleItem){
            ci.cancel();
            for(; this.options.keyTogglePerspective.consumeClick(); this.levelRenderer.needsUpdate()) {
                CameraType cameratype = this.options.getCameraType();
                this.options.setCameraType(this.options.getCameraType().cycle());
                if (cameratype.isFirstPerson() != this.options.getCameraType().isFirstPerson()) {
                    this.gameRenderer.checkEntityPostEffect(this.options.getCameraType().isFirstPerson() ? this.getCameraEntity() : null);
                }
            }

            while(this.options.keySmoothCamera.consumeClick()) {
                this.options.smoothCamera = !this.options.smoothCamera;
            }

            for(int i = 0; i < 9; ++i) {
                boolean flag = this.options.keySaveHotbarActivator.isDown();
                boolean flag1 = this.options.keyLoadHotbarActivator.isDown();
                if (this.options.keyHotbarSlots[i].consumeClick()) {
                    if (this.player.isSpectator()) {
                        this.gui.getSpectatorGui().onHotbarSelected(i);
                    } else if (!this.player.isCreative() || this.screen != null || !flag1 && !flag) {
                        this.player.getInventory().selected = i;
                    } else {
                        CreativeModeInventoryScreen.handleHotbarLoadOrSave((Minecraft)(Object)this, i, flag1, flag);
                    }
                }
            }

            while(this.options.keySocialInteractions.consumeClick()) {
                if (!this.isMultiplayerServer()) {
                    this.player.displayClientMessage(SOCIAL_INTERACTIONS_NOT_AVAILABLE, true);
                    this.narrator.sayNow(SOCIAL_INTERACTIONS_NOT_AVAILABLE);
                } else {
                    if (this.socialInteractionsToast != null) {
                        this.tutorial.removeTimedToast(this.socialInteractionsToast);
                        this.socialInteractionsToast = null;
                    }

                    this.setScreen(new SocialInteractionsScreen());
                }
            }

            while(this.options.keyInventory.consumeClick()) {
                if (this.gameMode.isServerControlledInventory()) {
                    this.player.sendOpenInventory();
                } else {
                    this.tutorial.onOpenInventory();
                    this.setScreen(new InventoryScreen(this.player));
                }
            }

            while(this.options.keyAdvancements.consumeClick()) {
                this.setScreen(new AdvancementsScreen(this.player.connection.getAdvancements()));
            }

            while(this.options.keySwapOffhand.consumeClick()) {
                if (!this.player.isSpectator()) {
                    this.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                }
            }

            while(this.options.keyDrop.consumeClick()) {
                if (!this.player.isSpectator() && this.player.drop(Screen.hasControlDown())) {
                    this.player.swing(InteractionHand.MAIN_HAND);
                }
            }

            while(this.options.keyChat.consumeClick()) {
                this.openChatScreen("");
            }

            if (this.screen == null && this.overlay == null && this.options.keyCommand.consumeClick()) {
                this.openChatScreen("/");
            }

            boolean flag2 = false;
            if (this.player.isUsingItem()) {
                if (!this.options.keyUse.isDown() && !this.options.keyAttack.isDown()) {
                    this.gameMode.releaseUsingItem(this.player);
                }

            } else {
                while(this.options.keyUse.consumeClick() || this.options.keyAttack.consumeClick()) {
                    this.startUseItem();
                }

                while(this.options.keyPickItem.consumeClick()) {
                    this.pickBlock();
                }
            }

            if ((this.options.keyUse.isDown() || this.options.keyAttack.isDown()) && this.rightClickDelay == 0 && !this.player.isUsingItem()) {
                this.startUseItem();
            }

            this.continueAttack(this.screen == null && !flag2 && this.options.keyAttack.isDown() && this.mouseHandler.isMouseGrabbed());
        }
     }
}
