package com.phasetranscrystal.fpsmatch.client.screen.hud;

import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.client.tab.TabRenderer;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;

import java.util.*;

public class CSGameTabRenderer implements TabRenderer {
    private static final ResourceLocation GUI_ICONS_LOCATION = new ResourceLocation("textures/gui/icons.png");
    private final Component header = Component.literal("FPSMatch")
            .setStyle(Style.EMPTY.withBold(true).withColor(RenderUtil.color(40,255,128)));
    private final Minecraft minecraft = Minecraft.getInstance();

    @Override
    public String getGameType() {
        return "cs";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int windowWidth, List<PlayerInfo> playerInfoList, Scoreboard scoreboard, Objective objective) {
        if (!"cs".equals(ClientData.currentGameType)) return;
        
        // 列宽定义（总宽度400px）
        int padding = 5;
        int pingWidth = 40;      
        int avatarSize = 12;
        int nameWidth = 110;     
        int moneyWidth = 40;     
        int killWidth = 35;      
        int deathWidth = 35;     
        int assistWidth = 35;    
        int headshotWidth = 40;  
        int damageWidth = 48;    
        
        // 玩家信息区域固定尺寸
        int playerAreaWidth = 400;
        int playerRowHeight = 12;
        int playerGap = 2;
        // 减小队伍间隔
        int headerHeight = 12;
        
        // 背景尺寸（玩家信息栏+边距）
        int bgPadding = 10;
        int bgWidth = playerAreaWidth + bgPadding * 2;
        int bgHeight = 280; // 保持280px高度
        
        // 背景位置（屏幕居中）
        int bgX = (windowWidth - bgWidth) / 2;
        int bgY = (minecraft.getWindow().getGuiScaledHeight() - bgHeight) / 2;
        
        // 渲染背景
        guiGraphics.fill(bgX, bgY, bgX + bgWidth, bgY + bgHeight, 0x80000000);
        
        // 添加中间分割线
        int dividerY = bgY + bgHeight/2;
        guiGraphics.fill(bgX, dividerY, bgX + bgWidth, dividerY + 1, 0x40FFFFFF);
        
        // 计算玩家信息区域
        int teamContentHeight = (playerRowHeight + playerGap) * 5 - playerGap; // 5行玩家的总高度
        
        // 计算上半部分中心点
        int upperCenterY = bgY + bgHeight/4;
        // 计算下半部分中心点
        int lowerCenterY = bgY + bgHeight*3/4;
        
        // 计算CT玩家起始Y坐标（上半部分居中）
        int ctStartY = upperCenterY - teamContentHeight/2;
        
        // 计算T玩家起始Y坐标（下半部分居中）
        int tStartY = lowerCenterY - teamContentHeight/2;
        
        // 表头位置（在CT第一行上方）
        int headerY = ctStartY - headerHeight - 2;
        
        // 渲染表头
        int currentHeaderX = bgX + bgPadding;
        
        // Ping图标（满格）
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        guiGraphics.blit(GUI_ICONS_LOCATION, currentHeaderX + (pingWidth - 10) / 2, headerY + 2, 0, 176, 10, 8);
        guiGraphics.pose().popPose();
        currentHeaderX += pingWidth;
        
        // 占位
        currentHeaderX += avatarSize + nameWidth + padding;
        
        // 金钱
        Component moneyText = Component.translatable("fpsmatch.tab.header.money");
        guiGraphics.drawString(minecraft.font, moneyText, 
            currentHeaderX + (moneyWidth - minecraft.font.width(moneyText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += moneyWidth;
        
        // K/D/A
        Component killsText = Component.translatable("fpsmatch.tab.header.kills");
        guiGraphics.drawString(minecraft.font, killsText, 
            currentHeaderX + (killWidth - minecraft.font.width(killsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += killWidth;
        
        Component deathsText = Component.translatable("fpsmatch.tab.header.deaths");
        guiGraphics.drawString(minecraft.font, deathsText, 
            currentHeaderX + (deathWidth - minecraft.font.width(deathsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += deathWidth;
        
        Component assistsText = Component.translatable("fpsmatch.tab.header.assists");
        guiGraphics.drawString(minecraft.font, assistsText, 
            currentHeaderX + (assistWidth - minecraft.font.width(assistsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += assistWidth;
        
        // 爆头率
        Component headshotText = Component.translatable("fpsmatch.tab.header.headshot");
        guiGraphics.drawString(minecraft.font, headshotText, 
            currentHeaderX + (headshotWidth - minecraft.font.width(headshotText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += headshotWidth;
        
        // 伤害（直接跟在爆头率后面）
        Component damageText = Component.translatable("fpsmatch.tab.header.damage");
        guiGraphics.drawString(minecraft.font, damageText, 
            currentHeaderX + (damageWidth - minecraft.font.width(damageText)) / 2, headerY, 0xFFFFFFFF);
        
        // 过滤并排序玩家
        Map<String, List<PlayerInfo>> teamPlayers = new HashMap<>();
        teamPlayers.put("ct", new ArrayList<>());
        teamPlayers.put("t", new ArrayList<>());

        for (PlayerInfo info : playerInfoList) {
            String team = ClientData.getTeamByUUID(info.getProfile().getId());
            TabData tabData = ClientData.getTabDataByUUID(info.getProfile().getId());
            if (team != null && tabData != null) {
                teamPlayers.get(team).add(info);
            }
        }

        // 按伤害排序
        Comparator<PlayerInfo> damageComparator = (p1, p2) -> {
            TabData t1 = ClientData.getTabDataByUUID(p1.getProfile().getId());
            TabData t2 = ClientData.getTabDataByUUID(p2.getProfile().getId());
            return Float.compare(t2.getDamage(), t1.getDamage());
        };

        teamPlayers.get("ct").sort(damageComparator);
        teamPlayers.get("t").sort(damageComparator);

        // 渲染CT玩家（从顶部开始）
        int currentY = ctStartY;
        List<PlayerInfo> ctPlayers = teamPlayers.get("ct");
        for (int i = 0; i < 5; i++) {
            if (i < ctPlayers.size()) {
                renderPlayerRow(guiGraphics, ctPlayers.get(i), bgX + bgPadding, currentY, playerAreaWidth, playerRowHeight, CSGameOverlay.textCTWinnerRoundsColor);
            } else {
                renderEmptyPlayerRow(guiGraphics, bgX + bgPadding, currentY, playerAreaWidth, playerRowHeight, CSGameOverlay.textCTWinnerRoundsColor);
            }
            currentY += playerRowHeight + playerGap;
        }
        
        // 渲染T玩家（从中间往下）
        currentY = tStartY;
        List<PlayerInfo> tPlayers = teamPlayers.get("t");
        for (int i = 0; i < 5; i++) {
            if (i < tPlayers.size()) {
                renderPlayerRow(guiGraphics, tPlayers.get(i), bgX + bgPadding, currentY, playerAreaWidth, playerRowHeight, CSGameOverlay.textTWinnerRoundsColor);
            } else {
                renderEmptyPlayerRow(guiGraphics, bgX + bgPadding, currentY, playerAreaWidth, playerRowHeight, CSGameOverlay.textTWinnerRoundsColor);
            }
            currentY += playerRowHeight + playerGap;
        }
    }

    private void renderPlayerRow(GuiGraphics guiGraphics, PlayerInfo player, int x, int y, int width, int height, int textColor) {
        TabData tabData = ClientData.getTabDataByUUID(player.getProfile().getId());
        String playerTeam = ClientData.getTeamByUUID(player.getProfile().getId());
        String localTeam = ClientData.getTeamByUUID(minecraft.player.getUUID());
        boolean isSameTeam = Objects.equals(playerTeam, localTeam);
        boolean isLocalPlayer = player.getProfile().getId().equals(minecraft.player.getUUID());
        
        // 背景 - 如果是本地玩家，使用对应队伍颜色的背景
        int bgColor = isLocalPlayer ? (textColor & 0xFFFFFF) | 0x40000000 : 0x40000000;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);
        
        // 列宽定义（总宽度400px）
        int padding = 5;
        int pingWidth = 40;      
        int avatarSize = 12;
        int nameWidth = 110;     
        int moneyWidth = 40;     
        int killWidth = 35;      
        int deathWidth = 35;     
        int assistWidth = 35;    
        int headshotWidth = 40;  
        int damageWidth = 48;    
        
        int textY = y + (height - 8) / 2;
        int currentX = x;
        
        // Ping值
        String pingText = String.valueOf(player.getLatency());
        guiGraphics.drawString(minecraft.font, pingText, 
            currentX + (pingWidth - minecraft.font.width(pingText)) / 2, textY, RenderUtil.color(25,180,60));
        currentX += pingWidth;
        
        // 头像
        PlayerFaceRenderer.draw(guiGraphics, player.getSkinLocation(), currentX, y, avatarSize);
        currentX += avatarSize + padding;
        
        // 玩家名（左对齐）
        guiGraphics.drawString(minecraft.font, getNameForDisplay(player), currentX, textY, textColor);
        
        // 金钱（与表头对齐）
        int moneyX = x + pingWidth + avatarSize + padding + nameWidth;
        // 始终显示高亮背景
        guiGraphics.fill(moneyX, y, moneyX + moneyWidth, y + height, 0x20FFFFFF);
        // 只在同队时显示金钱数值
        if (isSameTeam) {
            String money = "$" + ClientData.playerMoney.getOrDefault(player.getProfile().getId(), 0);
            guiGraphics.drawString(minecraft.font, money, 
                moneyX + (moneyWidth - minecraft.font.width(money)) / 2, textY, textColor);
        }
        
        // K/D/A（与表头对齐）
        int kdaX = moneyX + moneyWidth;
        String kills = String.valueOf(tabData.getKills());
        guiGraphics.drawString(minecraft.font, kills, 
            kdaX + (killWidth - minecraft.font.width(kills)) / 2, textY, textColor);
        
        int deathsX = kdaX + killWidth;
        guiGraphics.fill(deathsX, y, deathsX + deathWidth, y + height, 0x20FFFFFF);
        String deaths = String.valueOf(tabData.getDeaths());
        guiGraphics.drawString(minecraft.font, deaths, 
            deathsX + (deathWidth - minecraft.font.width(deaths)) / 2, textY, textColor);
        
        int assistsX = deathsX + deathWidth;
        String assists = String.valueOf(tabData.getAssists());
        guiGraphics.drawString(minecraft.font, assists, 
            assistsX + (assistWidth - minecraft.font.width(assists)) / 2, textY, textColor);
        
        // 爆头率（与表头对齐）
        int headshotX = assistsX + assistWidth;
        guiGraphics.fill(headshotX, y, headshotX + headshotWidth, y + height, 0x20FFFFFF);
        String headshotPercentage = tabData.getKills() > 0 
            ? String.format("%.0f%%", (float)tabData.getHeadshotKills() / tabData.getKills() * 100)
            : "0%";
        guiGraphics.drawString(minecraft.font, headshotPercentage, 
            headshotX + (headshotWidth - minecraft.font.width(headshotPercentage)) / 2, textY, textColor);
        
        // 伤害（与表头对齐）
        int damageX = x + width - damageWidth;
        guiGraphics.fill(damageX, y, damageX + damageWidth, y + height, 0x40FFFFFF);
        String damage = String.valueOf(Math.round(tabData.getDamage()));
        guiGraphics.drawString(minecraft.font, damage, 
            damageX + (damageWidth - minecraft.font.width(damage)) / 2, textY, textColor);

        if(!tabData.isLiving()){
            //渲染一层半透明灰色
            guiGraphics.fill(x, y, x + width, y + height, 0x40000000);
        }
    }

    private void renderEmptyPlayerRow(GuiGraphics guiGraphics, int x, int y, int width, int height, int textColor) {
        // 列宽定义（与表头保持一致）
        int padding = 5;
        int pingWidth = 40;
        int avatarSize = 12;
        int nameWidth = 110;
        int moneyWidth = 40;
        int killWidth = 35;
        int deathWidth = 35;
        int assistWidth = 35;
        int headshotWidth = 40;
        int damageWidth = 48;
        
        // 渲染半透明背景
        guiGraphics.fill(x, y, x + width, y + height, 0x40000000);
        
        // 渲染高亮列的背景
        int moneyX = x + pingWidth + avatarSize + padding + nameWidth;
        guiGraphics.fill(moneyX, y, moneyX + moneyWidth, y + height, 0x20FFFFFF);
        
        int deathsX = moneyX + moneyWidth + killWidth;
        guiGraphics.fill(deathsX, y, deathsX + deathWidth, y + height, 0x20FFFFFF);
        
        int headshotX = deathsX + deathWidth + assistWidth;
        guiGraphics.fill(headshotX, y, headshotX + headshotWidth, y + height, 0x20FFFFFF);
        
        int damageX = x + width - damageWidth;
        guiGraphics.fill(damageX, y, damageX + damageWidth, y + height, 0x40FFFFFF);
    }

    private Component getNameForDisplay(PlayerInfo info) {
        return info.getTabListDisplayName() != null ? info.getTabListDisplayName() : Component.literal(info.getProfile().getName());
    }
} 