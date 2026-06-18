package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMGuiTheme;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapDetailChildScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapManageScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapScreenBase;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.FPSMMapSelectScreens;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ScrollableList;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomPlayerInfo;
import com.phasetranscrystal.fpsmatch.common.packet.mapselect.MapRoomTeamInfo;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 队伍管理界面。
 * <p>
 * 顶部显示队伍按钮，玩家可点击切换自己的队伍（队伍已满则禁用）。
 * 中部按队伍分组展示玩家静态头像、名称与准备状态。
 * 管理员可选中玩家后在底部面板将其移动至其他队伍或踢出。
 * 底部提供准备/取消准备、地图管理（管理员）与返回详情页入口。
 */
public class FPSMTeamManageScreen extends FPSMMapScreenBase implements FPSMMapDetailChildScreen {
    private static final int TEAM_BUTTON_Y = 74;
    private static final int LIST_TOP = 104;
    private static final int LIST_BOTTOM_OFFSET = 90;
    private static final int ROW_HEIGHT = 24;
    private static final int AVATAR_SIZE = 16;
    private static final int STATUS_DOT_SIZE = 6;
    private static final int PANEL_WIDTH = 400;
    private static final int ADMIN_PANEL_HEIGHT = 46;

    private MapRoomDetail detail;
    private final Screen parent;
    private final Set<UUID> syncedReadyPlayers = new HashSet<>();
    private int syncedCountdownSeconds = -1;

    private Button readyButton;
    private Button manageButton;
    private final List<Button> teamButtons = new ArrayList<>();
    private final List<Button> kickButtons = new ArrayList<>();
    private final List<Button> adminMoveButtons = new ArrayList<>();
    private ScrollableList list;
    private UUID selectedPlayer;
    private String selectedPlayerTeam;

    public FPSMTeamManageScreen(MapRoomDetail detail, Screen parent) {
        super(Component.translatable("gui.fpsm.team_manage.title"));
        this.detail = detail;
        this.parent = parent;
    }

    @Override
    public void applyDetail(MapRoomDetail detail) {
        this.detail = detail;
        this.syncedReadyPlayers.clear();
        this.syncedReadyPlayers.addAll(detail.readyPlayers());
        this.syncedCountdownSeconds = detail.summary().readyCountdownSeconds();
        this.rebuildWidgets();
    }

    public void applyReadyState(String gameType, String mapName, int countdownSeconds, Set<UUID> readyPlayers) {
        if (detail == null || !detail.summary().gameType().equals(gameType) || !detail.summary().mapName().equals(mapName)) {
            return;
        }
        this.syncedCountdownSeconds = countdownSeconds;
        this.syncedReadyPlayers.clear();
        this.syncedReadyPlayers.addAll(readyPlayers);
        updateReadyButton();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        teamButtons.clear();
        kickButtons.clear();
        adminMoveButtons.clear();
        int centerX = width / 2;

        buildTeamButtons(centerX);
        buildPlayerList(centerX);
        buildBottomAndAdminPanel(centerX);
    }

    private void buildTeamButtons(int centerX) {
        List<MapRoomTeamInfo> teams = detail.teams().stream()
                .filter(t -> !t.spectator())
                .sorted(Comparator.comparing(MapRoomTeamInfo::name))
                .toList();
        if (teams.isEmpty()) return;

        int total = teams.size();
        int bw = FPSMGuiTheme.BUTTON_MEDIUM_WIDTH;
        int gap = FPSMGuiTheme.BUTTON_GAP;
        int totalWidth = total * bw + (total - 1) * gap;
        int startX = centerX - totalWidth / 2;

        String selfTeam = selfTeamName();
        for (int i = 0; i < teams.size(); i++) {
            MapRoomTeamInfo team = teams.get(i);
            final String teamName = team.name();
            boolean current = teamName.equals(selfTeam);
            boolean full = team.isFull();
            boolean active = !current && !full && canSwitchTeam();
            Button button = addRenderableWidget(Button.builder(Component.literal(teamName.toUpperCase()), b -> switchSelfTeam(teamName))
                    .bounds(startX + i * (bw + gap), TEAM_BUTTON_Y, bw, FPSMGuiTheme.BUTTON_HEIGHT)
                    .build());
            button.active = active;
            teamButtons.add(button);
        }
    }

    private int teamColor(String teamName) {
        return switch (teamName.toLowerCase(Locale.ROOT)) {
            case "ct" -> 0xFF4080FF;
            case "t" -> 0xFFFFC040;
            default -> FPSMGuiTheme.TEXT_SUB;
        };
    }

    private void buildPlayerList(int centerX) {
        int left = centerX - PANEL_WIDTH / 2;
        int bottom = height - LIST_BOTTOM_OFFSET - (hasAdminPanel() ? ADMIN_PANEL_HEIGHT : 0);

        List<MapRoomPlayerInfo> allPlayers = detail.players();
        List<MapRoomTeamInfo> normalTeams = detail.teams().stream()
                .filter(t -> !t.spectator())
                .sorted(Comparator.comparing(MapRoomTeamInfo::name))
                .toList();

        for (MapRoomPlayerInfo player : allPlayers) {
            if (isSpectator(player)) continue;
            Button kick = createSmallButton(Component.translatable("gui.fpsm.map_select.kick"), left + PANEL_WIDTH - 78, 0,
                    b -> sendAction(MapRoomActionC2SPacket.Action.KICK, player.uuid()));
            kick.active = detail.summary().currentPlayerOp();
            addRenderableWidget(kick);
            kickButtons.add(kick);
        }

        list = new ScrollableList(left, LIST_TOP, PANEL_WIDTH, bottom, ROW_HEIGHT, 0) {
            @Override
            public int totalItems() {
                int count = 0;
                for (MapRoomTeamInfo team : normalTeams) {
                    count++;
                    count += playerCountInTeam(team.name());
                }
                return count;
            }

            @Override
            protected void renderRow(GuiGraphics graphics, int index, int rowTop, int mouseX, int mouseY) {
                int pos = 0;
                for (MapRoomTeamInfo team : normalTeams) {
                    if (pos == index) {
                        renderTeamHeader(graphics, team, left, rowTop);
                        return;
                    }
                    pos++;
                    List<MapRoomPlayerInfo> teamPlayers = playersInTeam(team.name());
                    if (index >= pos && index < pos + teamPlayers.size()) {
                        int playerIndex = index - pos;
                        renderPlayerRow(graphics, teamPlayers.get(playerIndex), left, rowTop, playerIndex, mouseX, mouseY);
                        return;
                    }
                    pos += teamPlayers.size();
                }
            }
        };
    }

    private void buildBottomAndAdminPanel(int centerX) {
        boolean op = detail.summary().currentPlayerOp();
        int bottomY = height - 52;
        int bw = FPSMGuiTheme.BUTTON_MEDIUM_WIDTH;
        int gap = FPSMGuiTheme.BUTTON_GAP;

        if (hasAdminPanel()) {
            int panelTop = bottomY - ADMIN_PANEL_HEIGHT;
            MapRoomPlayerInfo player = findPlayer(selectedPlayer);
            if (player != null) {
                List<String> otherTeams = detail.teams().stream()
                        .filter(t -> !t.spectator())
                        .map(MapRoomTeamInfo::name)
                        .filter(n -> !n.equals(selectedPlayerTeam))
                        .sorted()
                        .toList();
                if (!otherTeams.isEmpty()) {
                    int smallBw = FPSMGuiTheme.BUTTON_SMALL_WIDTH;
                    int smallGap = 4;
                    int totalWidth = otherTeams.size() * smallBw + (otherTeams.size() - 1) * smallGap;
                    int startX = centerX - totalWidth / 2;
                    for (int i = 0; i < otherTeams.size(); i++) {
                        final String target = otherTeams.get(i);
                        Button btn = addRenderableWidget(createSmallButton(
                                Component.translatable("gui.fpsm.team_manage.move_btn", target.toUpperCase()),
                                startX + i * (smallBw + smallGap), panelTop + 20,
                                b -> moveSelectedPlayer(target)));
                        adminMoveButtons.add(btn);
                    }
                }
                Button cancel = addRenderableWidget(createSmallButton(Component.translatable("gui.fpsm.team_manage.cancel"),
                        centerX + 140, panelTop + 20, b -> clearSelection()));
                adminMoveButtons.add(cancel);
            }
        }

        int total = op ? 3 : 2;
        int totalWidth = total * bw + (total - 1) * gap;
        int startX = centerX - totalWidth / 2;

        readyButton = addRenderableWidget(createMediumButton(Component.empty(), startX, bottomY, b -> toggleReady()));
        updateReadyButton();
        if (op) {
            manageButton = addRenderableWidget(createMediumButton(Component.translatable("gui.fpsm.map_select.manage"), startX + bw + gap, bottomY,
                    b -> FPSMMapSelectScreens.openChild(new FPSMMapManageScreen(detail, this))));
            manageButton.active = op;
        }
        addRenderableWidget(createMediumButton(Component.translatable("gui.back"), startX + (total - 1) * (bw + gap), bottomY, b -> onClose()));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawMultiLayerBackground(graphics);

        graphics.drawCenteredString(font, title, width / 2, 18, FPSMGuiTheme.TEXT_TITLE);
        graphics.drawCenteredString(font, Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()), width / 2, 36, FPSMGuiTheme.TEXT_SUB);

        int left = width / 2 - PANEL_WIDTH / 2;
        int bottom = height - LIST_BOTTOM_OFFSET - (hasAdminPanel() ? ADMIN_PANEL_HEIGHT : 0);
        drawListBackground(graphics, left - 6, LIST_TOP - 6, left + PANEL_WIDTH + 6, bottom + 6);

        renderReadySummary(graphics, left);
        renderAdminPanel(graphics);

        if (list != null) {
            hideActionButtons();
            list.render(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // 在队伍按钮顶部绘制队伍色条，标识当前/可用/禁用状态
        for (Button btn : teamButtons) {
            int color = teamColor(btn.getMessage().getString());
            int barColor = btn.active ? color : FPSMGuiTheme.TEXT_DISABLED;
            graphics.fill(btn.getX(), btn.getY(), btn.getX() + btn.getWidth(), btn.getY() + 2, barColor);
        }
    }

    private void renderReadySummary(GuiGraphics graphics, int left) {
        int ready = readyNormalPlayerCount();
        int total = normalPlayerCount();
        Component text = Component.translatable("gui.fpsm.team_manage.ready_summary", ready, total);
        int color = ready == total && total > 0 ? FPSMGuiTheme.ST_WAITING : FPSMGuiTheme.TEXT_SUB;
        graphics.drawString(font, text, left, LIST_TOP - 18, color, false);

        if (syncedCountdownSeconds > 0 && ready == total && total > 0) {
            Component countdown = Component.translatable("gui.fpsm.team_manage.countdown", syncedCountdownSeconds)
                    .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD);
            graphics.drawString(font, countdown, left + font.width(text) + 12, LIST_TOP - 18, FPSMGuiTheme.ST_WAITING, false);
        }
    }

    private void renderAdminPanel(GuiGraphics graphics) {
        if (!hasAdminPanel()) return;
        int panelTop = height - 52 - ADMIN_PANEL_HEIGHT;
        graphics.fill(width / 4, panelTop, width * 3 / 4, panelTop + ADMIN_PANEL_HEIGHT, FPSMGuiTheme.BG_PANEL);

        MapRoomPlayerInfo player = findPlayer(selectedPlayer);
        if (player == null) return;
        Component hint = Component.translatable("gui.fpsm.team_manage.selected", player.name(), selectedPlayerTeam.toUpperCase());
        graphics.drawCenteredString(font, hint, width / 2, panelTop + 4, FPSMGuiTheme.TEXT_HIGHLIGHT);
    }

    private void renderTeamHeader(GuiGraphics graphics, MapRoomTeamInfo team, int left, int rowTop) {
        graphics.fill(left, rowTop, left + PANEL_WIDTH, rowTop + ROW_HEIGHT, FPSMGuiTheme.BG_PANEL);
        Component name = Component.literal(team.name().toUpperCase()).withStyle(net.minecraft.ChatFormatting.BOLD);
        Component count = Component.literal(team.currentPlayers() + "/" + (team.playerLimit() < 0 ? "∞" : team.playerLimit()));
        graphics.drawString(font, name, left + 8, rowTop + 8, FPSMGuiTheme.TEXT_TITLE, false);
        graphics.drawString(font, count, left + PANEL_WIDTH - 60, rowTop + 8, FPSMGuiTheme.TEXT_SUB, false);
    }

    private void renderPlayerRow(GuiGraphics graphics, MapRoomPlayerInfo player, int left, int rowTop, int visibleIndex, int mouseX, int mouseY) {
        boolean selected = player.uuid().equals(selectedPlayer);
        boolean hovered = mouseX >= left && mouseX < left + PANEL_WIDTH
                && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
        if (selected) {
            graphics.fill(left, rowTop, left + PANEL_WIDTH, rowTop + ROW_HEIGHT, FPSMGuiTheme.ROW_SELECTED);
        } else if (hovered) {
            graphics.fill(left, rowTop, left + PANEL_WIDTH, rowTop + ROW_HEIGHT, FPSMGuiTheme.ROW_HOVER);
        } else if (visibleIndex % 2 == 0) {
            graphics.fill(left, rowTop, left + PANEL_WIDTH, rowTop + ROW_HEIGHT, FPSMGuiTheme.ROW_NORMAL);
        }

        renderPlayerHead(graphics, player.uuid(), player.name(), left + 8, rowTop + 4);

        // 在线状态圆点
        int dotColor = player.online() ? FPSMGuiTheme.ST_ONLINE : FPSMGuiTheme.TEXT_DISABLED;
        graphics.fill(left + 28, rowTop + 9, left + 28 + STATUS_DOT_SIZE, rowTop + 9 + STATUS_DOT_SIZE, dotColor);

        int nameColor = player.online() ? FPSMGuiTheme.TEXT_HIGHLIGHT : FPSMGuiTheme.TEXT_DISABLED;
        graphics.drawString(font, Component.literal(player.name()), left + 38, rowTop + 7, nameColor, false);

        if (isReady(player.uuid())) {
            Component mark = Component.literal("\u2714 ").append(Component.translatable("gui.fpsm.team_manage.ready_mark"));
            graphics.drawString(font, mark, left + 170, rowTop + 7, FPSMGuiTheme.ST_WAITING, false);
        }

        int buttonIndex = visiblePlayerIndex(player);
        if (buttonIndex >= 0) {
            Button kick = kickButtons.get(buttonIndex);
            kick.setX(left + PANEL_WIDTH - 78);
            kick.setY(rowTop + 2);
            kick.visible = (selected || hovered) && detail.summary().currentPlayerOp();
        }
    }

    private void renderPlayerHead(GuiGraphics graphics, UUID uuid, String name, int x, int y) {
        ResourceLocation skin = RenderUtil.fetchSkin(uuid, name);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        float scale = AVATAR_SIZE / 8.0f;
        graphics.pose().scale(scale, scale, 1f);
        graphics.blit(skin, 0, 0, 8, 8, 8, 8, 64, 64);
        graphics.blit(skin, 0, 0, 40, 8, 8, 8, 64, 64);
        graphics.pose().popPose();
    }

    private void hideActionButtons() {
        kickButtons.forEach(b -> b.visible = false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (list != null && detail.summary().currentPlayerOp()) {
            int index = list.indexAt(mouseX, mouseY);
            if (index >= 0) {
                MapRoomPlayerInfo player = playerAtIndex(index);
                if (player != null && !isHeaderIndex(index)) {
                    selectPlayer(player);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (list != null && list.handleMouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean hasAdminPanel() {
        return selectedPlayer != null && detail.summary().currentPlayerOp();
    }

    private void selectPlayer(MapRoomPlayerInfo player) {
        if (player.uuid().equals(selectedPlayer)) {
            selectedPlayer = null;
            selectedPlayerTeam = null;
        } else {
            selectedPlayer = player.uuid();
            selectedPlayerTeam = player.teamName();
        }
        rebuildWidgets();
    }

    private void clearSelection() {
        selectedPlayer = null;
        selectedPlayerTeam = null;
        rebuildWidgets();
    }

    private void switchSelfTeam(String teamName) {
        if (minecraft.player == null) return;
        sendAction(MapRoomActionC2SPacket.Action.SWITCH_TEAM, minecraft.player.getUUID(), teamName);
    }

    private void moveSelectedPlayer(String targetTeam) {
        if (selectedPlayer != null) {
            sendAction(MapRoomActionC2SPacket.Action.SWITCH_TEAM, selectedPlayer, targetTeam);
            selectedPlayer = null;
            selectedPlayerTeam = null;
        }
    }

    private void toggleReady() {
        if (minecraft.player == null) return;
        sendAction(MapRoomActionC2SPacket.Action.READY, minecraft.player.getUUID());
    }

    private void sendAction(MapRoomActionC2SPacket.Action action, UUID target) {
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, detail.summary().gameType(), detail.summary().mapName(), target));
    }

    private void sendAction(MapRoomActionC2SPacket.Action action, UUID target, String data) {
        FPSMatch.sendToServer(new MapRoomActionC2SPacket(action, detail.summary().gameType(), detail.summary().mapName(), target, data));
    }

    private void updateReadyButton() {
        if (readyButton == null || minecraft.player == null) return;
        boolean ready = isReady(minecraft.player.getUUID());
        readyButton.setMessage(ready
                ? Component.translatable("gui.fpsm.team_manage.ready.off")
                : Component.translatable("gui.fpsm.team_manage.ready.on"));
    }

    private boolean isReady(UUID uuid) {
        if (syncedReadyPlayers.contains(uuid)) return true;
        return detail.readyPlayers().contains(uuid);
    }

    private boolean isSpectator(MapRoomPlayerInfo player) {
        return detail.teams().stream()
                .filter(MapRoomTeamInfo::spectator)
                .anyMatch(t -> t.name().equals(player.teamName()));
    }

    private String selfTeamName() {
        if (minecraft.player == null) return "";
        UUID self = minecraft.player.getUUID();
        return detail.players().stream()
                .filter(p -> p.uuid().equals(self))
                .findFirst()
                .map(MapRoomPlayerInfo::teamName)
                .orElse("");
    }

    private boolean canSwitchTeam() {
        if (detail.summary().started() && !detail.summary().allowJoinInProgress()) return false;
        return detail.summary().currentPlayerJoined() || detail.summary().currentPlayerSpectating();
    }

    private int playerCountInTeam(String teamName) {
        return (int) detail.players().stream().filter(p -> p.teamName().equals(teamName)).count();
    }

    private List<MapRoomPlayerInfo> playersInTeam(String teamName) {
        return detail.players().stream()
                .filter(p -> p.teamName().equals(teamName))
                .sorted(Comparator.comparing(MapRoomPlayerInfo::name))
                .toList();
    }

    private int visiblePlayerIndex(MapRoomPlayerInfo player) {
        int idx = 0;
        for (MapRoomPlayerInfo p : detail.players()) {
            if (isSpectator(p)) continue;
            if (p.uuid().equals(player.uuid())) return idx;
            idx++;
        }
        return -1;
    }

    private boolean isHeaderIndex(int listIndex) {
        List<MapRoomTeamInfo> normalTeams = detail.teams().stream()
                .filter(t -> !t.spectator())
                .sorted(Comparator.comparing(MapRoomTeamInfo::name))
                .toList();
        int pos = 0;
        for (MapRoomTeamInfo team : normalTeams) {
            if (pos == listIndex) return true;
            pos++;
            pos += playerCountInTeam(team.name());
        }
        return false;
    }

    private MapRoomPlayerInfo playerAtIndex(int listIndex) {
        List<MapRoomTeamInfo> normalTeams = detail.teams().stream()
                .filter(t -> !t.spectator())
                .sorted(Comparator.comparing(MapRoomTeamInfo::name))
                .toList();
        int pos = 0;
        for (MapRoomTeamInfo team : normalTeams) {
            if (pos == listIndex) return null;
            pos++;
            List<MapRoomPlayerInfo> players = playersInTeam(team.name());
            if (listIndex >= pos && listIndex < pos + players.size()) {
                return players.get(listIndex - pos);
            }
            pos += players.size();
        }
        return null;
    }

    private MapRoomPlayerInfo findPlayer(UUID uuid) {
        return detail.players().stream().filter(p -> p.uuid().equals(uuid)).findFirst().orElse(null);
    }

    private int normalPlayerCount() {
        return (int) detail.players().stream().filter(p -> !isSpectator(p)).count();
    }

    private int readyNormalPlayerCount() {
        return (int) detail.players().stream()
                .filter(p -> !isSpectator(p))
                .filter(p -> isReady(p.uuid()))
                .count();
    }
}
