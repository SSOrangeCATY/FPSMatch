package com.phasetranscrystal.fpsmatch.mixin;

import com.phasetranscrystal.fpsmatch.common.item.BaseThrowAbleItem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.tutorial.Tutorial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

@Mixin(value = Minecraft.class, remap = false)
public abstract class MixinMinecraft {

    @Shadow(remap = false) @Nullable public LocalPlayer player;

    @Shadow(remap = false) @Final public Options options;

    @Shadow(remap = false) private int rightClickDelay;

    @Shadow(remap = false) @Nullable public MultiPlayerGameMode gameMode;

    @Shadow(remap = false) @Final public LevelRenderer levelRenderer;

    @Shadow(remap = false) @Final public GameRenderer gameRenderer;

    @Shadow(remap = false) @Final public Gui gui;

    @Shadow(remap = false) @Final public MouseHandler mouseHandler;

    @Shadow(remap = false) @Final private Tutorial tutorial;

    @Shadow(remap = false) protected abstract void startUseItem();

    @Shadow(remap = false) @Nullable public abstract Entity getCameraEntity();

    @Shadow(remap = false) protected abstract boolean startAttack();

    @Shadow(remap = false) protected abstract void pickBlockOrEntity();

    @Shadow(remap = false) protected abstract void continueAttack(boolean pLeftClick);

    @Shadow(remap = false) public abstract boolean hasControlDown();

    @Shadow(remap = false) @Nullable public abstract ClientPacketListener getConnection();

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true, remap = false)
    private void onHandleKeybinds(CallbackInfo ci) {
        if (this.player == null || this.gameMode == null) return;
        if (this.player.getOffhandItem().isEmpty() && this.player.getMainHandItem().isEmpty()) return;
        if (this.gui.screen() != null) return;
        if (this.player.getOffhandItem().getItem() instanceof BaseThrowAbleItem
                || this.player.getMainHandItem().getItem() instanceof BaseThrowAbleItem) {
            ci.cancel();
            while (this.options.keyTogglePerspective.consumeClick()) {
                CameraType cameraType = this.options.getCameraType();
                this.options.setCameraType(this.options.getCameraType().cycle());
                if (cameraType.isFirstPerson() != this.options.getCameraType().isFirstPerson()) {
                    this.gameRenderer.checkEntityPostEffect(this.options.getCameraType().isFirstPerson() ? this.getCameraEntity() : null);
                }

            }

            while (this.options.keySmoothCamera.consumeClick()) {
                this.options.smoothCamera = !this.options.smoothCamera;
            }

            this.gui.handleKeybinds();

            while (this.options.keyToggleSpectatorShaderEffects.consumeClick()) {
                this.gameRenderer.togglePostEffect();
            }

            for (int i = 0; i < 9; ++i) {
                boolean savePressed = this.options.keySaveHotbarActivator.isDown();
                boolean loadPressed = this.options.keyLoadHotbarActivator.isDown();
                if (this.options.keyHotbarSlots[i].consumeClick()) {
                    if (this.player.isSpectator()) {
                        this.gui.hud.getSpectatorGui().onHotbarSelected(i);
                    } else if (!this.player.hasInfiniteMaterials() || this.gui.screen() != null || !loadPressed && !savePressed) {
                        this.player.getInventory().setSelectedSlot(i);
                    } else {
                        CreativeModeInventoryScreen.handleHotbarLoadOrSave((Minecraft)(Object)this, i, loadPressed, savePressed);
                    }
                }
            }

            while (this.options.keyInventory.consumeClick()) {
                if (this.gameMode.isServerControlledInventory()) {
                    this.player.sendOpenInventory();
                } else {
                    this.tutorial.onOpenInventory();
                    this.gui.setScreen(new InventoryScreen(this.player));
                }
            }

            while (this.options.keySwapOffhand.consumeClick()) {
                if (!this.player.isSpectator()) {
                    ClientPacketListener connection = this.getConnection();
                    if (connection != null) {
                        connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                    }
                }
            }

            while (this.options.keyDrop.consumeClick()) {
                if (!this.player.isSpectator() && this.player.drop(this.hasControlDown())) {
                    this.player.swing(InteractionHand.MAIN_HAND);
                }
            }

            boolean instantAttack = false;
            if (this.player.isUsingItem()) {
                if (!this.options.keyUse.isDown()) {
                    this.gameMode.releaseUsingItem(this.player);
                }

                while (this.options.keyAttack.consumeClick()) {
                }

                while (this.options.keyUse.consumeClick()) {
                }

                while (this.options.keyPickItem.consumeClick()) {
                }
            } else {
                while (this.options.keyUse.consumeClick() || this.options.keyAttack.consumeClick()) {
                    this.startUseItem();
                }

                while (this.options.keyPickItem.consumeClick()) {
                    this.pickBlockOrEntity();
                }

                if (this.player.isSpectator()) {
                    while (this.options.keySpectatorHotbar.consumeClick()) {
                        this.gui.hud.getSpectatorGui().onHotbarActionKeyPressed();
                    }
                }
            }

            if ((this.options.keyUse.isDown() || this.options.keyAttack.isDown()) && this.rightClickDelay == 0 && !this.player.isUsingItem()) {
                this.startUseItem();
            }

            this.continueAttack(this.gui.screen() == null && !instantAttack && this.options.keyAttack.isDown() && this.mouseHandler.isMouseGrabbed());
        }
    }
}
