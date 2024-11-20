package com.phasetranscrystal.fpsmatch.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay{
    private static final ResourceLocation GUI_ICONS_LOCATION = new ResourceLocation("textures/gui/icons.png");
    @Final
    @Shadow
    private  Minecraft minecraft;
    private final Component footer = Component.literal("END").setStyle(Style.EMPTY.withBold(true).withColor(RenderUtil.color(40,255,128)));
    private final Component header = Component.literal("FPSMatch").setStyle(Style.EMPTY.withBold(true).withColor(RenderUtil.color(40,255,128)));
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
            int scoreWidth = this.minecraft.font.width(" " + ClientData.tabData.getOrDefault(playerInfo.getProfile().getId(),new TabData(playerInfo.getProfile().getId())).getTabString());
            maxScoreWidth = Math.max(maxScoreWidth, scoreWidth);
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
        int scoreTextWidth = maxScoreWidth + 200;

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

                // 绘制ping图标和值
                this.fPSMatch$renderPingIcon(guiGraphics, itemWidth, x + 2, y, playerInfo);
                int avatarAndNameStartX = x + 28; // Ping图标和值之后的10像素加上图标宽度

                // 绘制玩家头像
                PlayerFaceRenderer.draw(guiGraphics, playerInfo.getSkinLocation(), avatarAndNameStartX, y, 8, isHatVisible, isUpsideDown);
                int nameAndScoreStartX = avatarAndNameStartX + 9; // 头像宽度

                // 绘制玩家名字
                guiGraphics.drawString(this.minecraft.font, this.getNameForDisplay(playerInfo), nameAndScoreStartX, y, playerInfo.getGameMode() == GameType.SPECTATOR ? -1862270977 : -1);

                // 计算tab分数的绘制起始位置
                int scoreTextStart = nameAndScoreStartX + maxNameWidth + 10; // 玩家名字之后的10像素加上名字宽度
                int scoreTextEnd = scoreTextStart + scoreTextWidth;

                // 绘制tab分数，居中显示在剩余空间中
                if (scoreTextEnd - scoreTextStart > 5) {
                    this.fPSMatch$renderTablistScore(y, scoreTextStart, scoreTextEnd, gameProfile.getId(), guiGraphics);
                }
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

    @Unique
    protected void fPSMatch$renderPingIcon(GuiGraphics pGuiGraphics, int itemWidth, int x, int y, PlayerInfo playerInfo) {
        int latencyIndex;
        if (playerInfo.getLatency() < 0) {
            latencyIndex = 5;
        } else if (playerInfo.getLatency() < 150) {
            latencyIndex = 0;
        } else if (playerInfo.getLatency() < 300) {
            latencyIndex = 1;
        } else if (playerInfo.getLatency() < 600) {
            latencyIndex = 2;
        } else if (playerInfo.getLatency() < 1000) {
            latencyIndex = 3;
        } else {
            latencyIndex = 4;
        }
        String text = String.valueOf(playerInfo.getLatency());
        int textWidth = Minecraft.getInstance().font.width(text);

        pGuiGraphics.pose().pushPose();
        pGuiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        // Ping图标绘制在x位置，y位置不变
        pGuiGraphics.blit(GUI_ICONS_LOCATION, x, y, 0, 176 + latencyIndex * 8, 10, 8);
        // Ping值文本绘制在图标右侧，加上图标的宽度和一点间距
        pGuiGraphics.drawString(Minecraft.getInstance().font, text, x + 12, y, RenderUtil.color(25,180,60));
        pGuiGraphics.pose().popPose();
    }


    @Unique
    private void fPSMatch$renderTablistScore(int pY, int start, int end, UUID pPlayerUuid, GuiGraphics pGuiGraphics) {
        String s = ClientData.tabData.getOrDefault(pPlayerUuid,new TabData(pPlayerUuid)).getTabString();
        pGuiGraphics.drawString(this.minecraft.font, s, start + (end - start) / 2 - this.minecraft.font.width(s), pY, 16777215);
    }

    @Shadow
    private List<PlayerInfo> getPlayerInfos() {
        return null;
    }


}
