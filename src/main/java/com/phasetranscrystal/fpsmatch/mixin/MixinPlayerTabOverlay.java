package com.phasetranscrystal.fpsmatch.mixin;

import com.google.common.io.ByteSource;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay{
    @Final
    @Shadow
    private  Minecraft minecraft;
    private Component footer = Component.literal("FPSM_TEST_END").setStyle(Style.EMPTY.withBold(true).withColor(RenderUtil.color(40,255,128)));
    private Component header = Component.literal("FPSM_TEST_HEAD").setStyle(Style.EMPTY.withBold(true).withColor(RenderUtil.color(40,255,128)));
    @Final
    @Shadow
    private Map<UUID, Object> healthStates;
    @Inject(at = {@At("HEAD")}, method = "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V", cancellable = true)
    public void fpsMatch$render$Custom(GuiGraphics guiGraphics, int windowWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        List<PlayerInfo> playerInfoList = this.getPlayerInfos();
        int maxNameWidth = 0;
        int maxScoreWidth = 0;
        if(playerInfoList == null) return;

        for (PlayerInfo playerInfo : playerInfoList) {
            int nameWidth = this.minecraft.font.width(this.getNameForDisplay(playerInfo));
            maxNameWidth = Math.max(maxNameWidth, nameWidth);
            if (objective != null && objective.getRenderType() != ObjectiveCriteria.RenderType.HEARTS) {
                int scoreWidth = this.minecraft.font.width(" " + scoreboard.getOrCreatePlayerScore(playerInfo.getProfile().getName(), objective).getScore());
                maxScoreWidth = Math.max(maxScoreWidth, scoreWidth);
            }
        }

        if (!this.healthStates.isEmpty()) {
            Set<UUID> playerIdSet = playerInfoList.stream()
                    .map((p) -> p.getProfile().getId())
                    .collect(Collectors.toSet());
            this.healthStates.keySet().removeIf((p) -> !playerIdSet.contains(p));
        }

        int playerCount = playerInfoList.size();
        int rowsPerPage = playerCount;

        int columnCount;
        for (columnCount = 1; rowsPerPage > 20; rowsPerPage = (playerCount + columnCount - 1) / columnCount) {
            ++columnCount;
        }
        boolean isLocalServerOrEncrypted = true;
        int scoreTextWidth;
        if (objective != null) {
            if (objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS) {
                scoreTextWidth = 90;
            } else {
                scoreTextWidth = maxScoreWidth;
            }
        } else {
            scoreTextWidth = 0;
        }

        int itemWidth = Math.min(columnCount * (isLocalServerOrEncrypted ? 9 : 0) + maxNameWidth + scoreTextWidth + 13, windowWidth - 50) / columnCount;
        int xStart = windowWidth / 2 - (itemWidth * columnCount + (columnCount - 1) * 5) / 2;
        int yStart = 10;
        int totalWidth = itemWidth * columnCount + (columnCount - 1) * 5;
        List<FormattedCharSequence> headerList = null;
        if (this.header != null) {
            headerList = this.minecraft.font.split(this.header, windowWidth - 50);

            for (FormattedCharSequence headerLine : headerList) {
                totalWidth = Math.max(totalWidth, this.minecraft.font.width(headerLine));
            }
        }

        List<FormattedCharSequence> footerList = null;
        if (this.footer != null) {
            footerList = this.minecraft.font.split(this.footer, windowWidth - 50);

            for (FormattedCharSequence footerLine : footerList) {
                totalWidth = Math.max(totalWidth, this.minecraft.font.width(footerLine));
            }
        }

        if (headerList != null) {
            guiGraphics.fill(windowWidth / 2 - totalWidth / 2 - 1, yStart - 1, windowWidth / 2 + totalWidth / 2 + 1, yStart + headerList.size() * 9, Integer.MIN_VALUE);

            for (FormattedCharSequence headerLine : headerList) {
                int headerLineWidth = this.minecraft.font.width(headerLine);
                guiGraphics.drawString(this.minecraft.font, headerLine, windowWidth / 2 - headerLineWidth / 2, yStart, -1);
                yStart += 9;
            }

            ++yStart;
        }

        guiGraphics.fill(windowWidth / 2 - totalWidth / 2 - 1, yStart - 1, windowWidth / 2 + totalWidth / 2 + 1, yStart + rowsPerPage * 9, Integer.MIN_VALUE);
        int backgroundColor = this.minecraft.options.getBackgroundColor(553648127);

        for (int i = 0; i < playerCount; ++i) {
            int row = i / rowsPerPage;
            int column = i % rowsPerPage;
            int x = xStart + row * itemWidth + row * 5;
            int y = yStart + column * 9;
            guiGraphics.fill(x, y, x + itemWidth, y + 8, backgroundColor);
            RenderSystem.enableBlend();
            if (i < playerInfoList.size()) {
                PlayerInfo playerInfo = playerInfoList.get(i);
                GameProfile gameProfile = playerInfo.getProfile();
                Player player = this.minecraft.level.getPlayerByUUID(gameProfile.getId());
                boolean isUpsideDown = player != null && LivingEntityRenderer.isEntityUpsideDown(player);
                boolean isHatVisible = player != null && player.isModelPartShown(PlayerModelPart.HAT);
                PlayerFaceRenderer.draw(guiGraphics, playerInfo.getSkinLocation(), x, y, 8, isHatVisible, isUpsideDown);
                x += 9;

                guiGraphics.drawString(this.minecraft.font, this.getNameForDisplay(playerInfo), x, y, playerInfo.getGameMode() == GameType.SPECTATOR ? -1862270977 : -1);
                if (objective != null && playerInfo.getGameMode() != GameType.SPECTATOR) {
                    int scoreTextStart = x + maxNameWidth + 1;
                    int scoreTextEnd = scoreTextStart + scoreTextWidth;
                    if (scoreTextEnd - scoreTextStart > 5) {
                        this.renderTablistScore(objective, y, gameProfile.getName(), scoreTextStart, scoreTextEnd, gameProfile.getId(), guiGraphics);
                    }
                }
                this.renderPingIcon(guiGraphics, itemWidth, x - 9, y, playerInfo);
            }
        }

        if (footerList != null) {
            yStart += rowsPerPage * 9 + 1;
            guiGraphics.fill(windowWidth / 2 - totalWidth / 2 - 1, yStart - 1, windowWidth / 2 + totalWidth / 2 + 1, yStart + footerList.size() * 9, Integer.MIN_VALUE);

            for (FormattedCharSequence footerLine : footerList) {
                int footerLineWidth = this.minecraft.font.width(footerLine);
                guiGraphics.drawString(this.minecraft.font, footerLine, windowWidth / 2 - footerLineWidth / 2, yStart, -1);
                yStart += 9;
            }
        }

        ci.cancel();
    }
    @Shadow
    public Component getNameForDisplay(PlayerInfo p_94550_) {
        return null;
    }

    @Shadow
    private void renderPingIcon(GuiGraphics pGuiGraphics, int i1, int i, int l2, PlayerInfo playerinfo1) {
    }

    @Shadow
    private void renderTablistScore(Objective pObjective, int l2, String name, int l4, int i5, UUID id, GuiGraphics pGuiGraphics) {
    }

    @Shadow
    private List<PlayerInfo> getPlayerInfos() {
        return null;
    }


}
