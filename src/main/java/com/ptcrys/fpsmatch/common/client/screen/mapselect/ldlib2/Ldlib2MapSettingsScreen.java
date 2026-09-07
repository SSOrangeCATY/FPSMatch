package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.AccessibleButton;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Backdrop;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSettingInfo;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSettingsC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomToastS2CPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Editable map-room settings list. */
public final class Ldlib2MapSettingsScreen extends Ldlib2MapChildScreen {
    private final UIElement sidebar;
    private final UIElement panel;
    private final Label subtitleLabel;
    private final Label pendingLabel;
    private final TextField searchField;
    private final AccessibleButton categoryFilterButton;
    private final UIElement categoryFilterPopup;
    private final VirtualScrollerView<String> categoryFilterList;
    private final AccessibleButton clearCategorySelectionButton;
    private final VirtualScrollerView<SettingListEntry> list;
    private final Label emptyLabel;
    private final AccessibleButton clearButton;
    private final AccessibleButton saveButton;
    private final AccessibleButton exitButton;
    private final Map<String, String> pendingValues = new LinkedHashMap<>();
    private final Set<String> selectedCategories = new LinkedHashSet<>();
    private List<String> availableCategories = List.of();
    private String searchQuery = "";
    private boolean saveInFlight;
    private boolean closeAfterSave;
    private int saveTicks;
    private boolean saveFailed;
    private Component saveFailureMessage;
    private int submittedChangeCount;
    private boolean discardConfirmation;
    private boolean compactEditor;

    public Ldlib2MapSettingsScreen(MapRoomDetail detail, Screen parent) {
        this(build(), detail, parent);
    }

    private Ldlib2MapSettingsScreen(Parts parts, MapRoomDetail detail, Screen parent) {
        super(parts.ui(), Component.translatable("gui.fpsm.map_select.settings.title"), detail, parent);
        this.sidebar = parts.sidebar();
        this.panel = parts.panel();
        this.subtitleLabel = parts.subtitle();
        this.pendingLabel = parts.pending();
        this.searchField = parts.search();
        this.categoryFilterButton = parts.categoryFilterButton();
        this.categoryFilterPopup = parts.categoryFilterPopup();
        this.categoryFilterList = parts.categoryFilterList();
        this.clearCategorySelectionButton = parts.clearCategorySelectionButton();
        this.list = parts.list();
        this.emptyLabel = parts.empty();
        this.clearButton = parts.clearButton();
        this.saveButton = parts.saveButton();
        this.exitButton = parts.exitButton();
        this.list.setItemUIProvider(this::listRow);
        this.categoryFilterList.setItemUIProvider(this::categoryFilterRow);
        this.searchField.setTextResponder(value -> {
            this.searchQuery = value == null ? "" : value.trim();
            refreshContent();
        });
        this.categoryFilterButton.setOnClick(e ->
                this.categoryFilterPopup.setVisible(!this.categoryFilterPopup.isVisible()));
        this.clearCategorySelectionButton.setOnClick(e -> clearCategorySelection());
        this.clearButton.setOnClick(e -> clearPendingChanges());
        this.saveButton.setOnClick(e -> {
            if (discardConfirmation) {
                cancelDiscard();
            } else {
                saveAndClose();
            }
        });
        this.exitButton.setOnClick(e -> {
            if (discardConfirmation) {
                discardAndClose();
            } else {
                requestClose();
            }
        });
        refreshContent();
    }

    @Override
    public void init() {
        super.init();
        applyResponsiveLayout();
        list.refreshVisibleItems();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        FPSMLdlib2Backdrop.drawMapIndex(graphics, width, height);
    }

    @Override
    public void tick() {
        super.tick();
        if (!saveInFlight) {
            return;
        }
        saveTicks++;
        if (saveTicks >= 200) {
            saveInFlight = false;
            closeAfterSave = false;
            saveFailed = true;
            saveFailureMessage = Component.translatable(
                    "gui.fpsm.map_select.settings.save_timeout");
            updatePendingState();
            announce(saveFailureMessage, true);
        }
    }

    public boolean isSavePending() {
        return saveInFlight;
    }

    public void applySaveFailure(MapRoomToastS2CPacket packet) {
        if (!saveInFlight) {
            return;
        }
        saveInFlight = false;
        saveTicks = 0;
        closeAfterSave = false;
        saveFailed = true;
        saveFailureMessage = packet.message();
        updatePendingState();
        announce(saveFailureMessage, true);
    }

    @Override
    protected void onDetailApplied() {
        if (!detail.summary().currentPlayerOp()) {
            pendingValues.clear();
            discardConfirmation = false;
        }
        prunePendingValues();
        if (saveInFlight && pendingValues.isEmpty()) {
            saveInFlight = false;
            saveTicks = 0;
            saveFailed = false;
            saveFailureMessage = null;
            if (closeAfterSave) {
                closeAfterSave = false;
                if (parent instanceof Ldlib2MapManageScreen manageScreen) {
                    manageScreen.showSettingsSaveSuccess(submittedChangeCount);
                }
                submittedChangeCount = 0;
                super.onClose();
                return;
            }
        }
        refreshContent();
    }

    private void refreshContent() {
        subtitleLabel.setValue(Component.literal(detail.summary().gameType() + " / " + detail.summary().mapName()));
        List<String> categories = MapSettingsGroupingModel.categories(detail.settings());
        selectedCategories.retainAll(categories);
        refreshCategoryFilter(categories);
        List<MapRoomSettingInfo> visible = detail.settings().stream()
                .filter(this::matchesSearch)
                .toList();
        List<SettingListEntry> entries = new ArrayList<>();
        for (MapSettingsGroupingModel.Group group : MapSettingsGroupingModel.group(visible, selectedCategories)) {
            entries.add(new CategoryEntry(group.category()));
            group.settings().forEach(setting -> entries.add(new SettingEntry(setting)));
        }
        list.setItems(entries);
        list.refreshVisibleItems();
        emptyLabel.setValue(Component.translatable(detail.settings().isEmpty()
                ? "gui.fpsm.map_select.settings.empty"
                : "gui.fpsm.map_select.settings.no_results"));
        emptyLabel.setVisible(entries.isEmpty());
        updatePendingState();
        if (width > 0 && height > 0) {
            applyResponsiveLayout();
        }
    }

    private void refreshCategoryFilter(List<String> categories) {
        if (!availableCategories.equals(categories)) {
            availableCategories = List.copyOf(categories);
            categoryFilterList.setItems(availableCategories);
            categoryFilterList.refreshVisibleItems();
        }
        if (categories.isEmpty()) {
            categoryFilterPopup.setVisible(false);
        }
        categoryFilterButton.setText(selectedCategories.isEmpty()
                ? Component.translatable("gui.fpsm.map_select.settings.category_filter")
                : Component.translatable("gui.fpsm.map_select.settings.category_filter.selected",
                        selectedCategories.size()));
        categoryFilterButton.setActive(!categories.isEmpty());
        clearCategorySelectionButton.setActive(!selectedCategories.isEmpty());
        categoryFilterList.refreshVisibleItems();
    }

    private UIElement categoryFilterRow(String category) {
        AccessibleButton toggle = new AccessibleButton();
        toggle.setId("fpsmatch.map_settings.category_filter.option." + category);
        Component categoryName = Component.translatable(
                MapRoomSettingInfo.categoryTranslationKey(category));
        toggle.setText(categoryName);
        toggle.setAccessibleName(categoryName);
        toggle.setAccessibleState(() -> toggleLabel(selectedCategories.contains(category)));
        toggle.layout(layout -> layout.widthPercent(100).height(20).marginBottom(3));
        FPSMMapSelectTheme.button(toggle, selectedCategories.contains(category)
                ? FPSMMapSelectTheme.ButtonKind.PRIMARY
                : FPSMMapSelectTheme.ButtonKind.SECONDARY);
        toggle.textStyle(style -> style.fontSize(9));
        toggle.style(style -> style.tooltips(Component.translatable(
                MapRoomSettingInfo.categoryTranslationKey(category))));
        toggle.setOnClick(event -> {
            if (!selectedCategories.remove(category)) {
                selectedCategories.add(category);
            }
            refreshContent();
            categoryFilterPopup.setVisible(true);
        });
        return toggle;
    }

    private void clearCategorySelection() {
        if (selectedCategories.isEmpty()) {
            return;
        }
        selectedCategories.clear();
        refreshContent();
        categoryFilterPopup.setVisible(true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (categoryFilterPopup.isVisible()) {
            UIElement target = hitElementAt(mouseX, mouseY);
            boolean insidePopup = target != null
                    && (target == categoryFilterPopup || categoryFilterPopup.isAncestorOf(target));
            boolean onFilterButton = target != null
                    && (target == categoryFilterButton || categoryFilterButton.isAncestorOf(target));
            if (target != null && !insidePopup && !onFilterButton) {
                categoryFilterPopup.setVisible(false);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void applyResponsiveLayout() {
        boolean stacked = width < 360 && height >= 300;
        int margin = Math.min(16, Math.max(8, width / 32));
        int gap = 8;
        int sidebarWidth;
        int sidebarHeight;
        int contentLeft;
        int contentTop;
        int contentWidth;
        int contentHeight;
        if (stacked) {
            sidebarWidth = Math.max(1, width - margin * 2);
            sidebarHeight = 92;
            contentLeft = margin;
            contentTop = margin + sidebarHeight + gap;
            contentWidth = sidebarWidth;
            contentHeight = Math.max(1, height - contentTop - margin);
        } else {
            int availableWidth = Math.max(2, width - margin * 2 - gap);
            sidebarWidth = Math.min(158, Math.max(104, availableWidth * 28 / 100));
            sidebarWidth = Math.min(sidebarWidth, Math.max(1, availableWidth * 42 / 100));
            sidebarHeight = Math.max(1, height - margin * 2);
            contentLeft = margin + sidebarWidth + gap;
            contentTop = margin;
            contentWidth = Math.max(1, width - contentLeft - margin);
            contentHeight = sidebarHeight;
        }
        absolute(sidebar, margin, margin, sidebarWidth, sidebarHeight);
        absolute(panel, contentLeft, contentTop, contentWidth, contentHeight);
        layoutSidebar(sidebarWidth, sidebarHeight, stacked);

        int padding = contentWidth >= 20 ? 7 : 1;
        int controlHeight = 22;
        boolean stackedToolbar = contentWidth < 230;
        int listTop;
        int filterWidth;
        if (stackedToolbar) {
            absolute(searchField, padding, 6, Math.max(1, contentWidth - padding * 2), controlHeight);
            absolute(categoryFilterButton, padding, 33,
                    Math.max(1, contentWidth - padding * 2), controlHeight);
            listTop = 61;
            filterWidth = Math.max(1, contentWidth - padding * 2);
        } else {
            int usableWidth = Math.max(1, contentWidth - padding * 2);
            int controlGap = 6;
            filterWidth = Math.min(126, Math.max(82, usableWidth * 34 / 100));
            int searchWidth = Math.max(1, usableWidth - filterWidth - controlGap);
            absolute(searchField, padding, 6, searchWidth, controlHeight);
            absolute(categoryFilterButton, padding + searchWidth + controlGap, 6,
                    filterWidth, controlHeight);
            listTop = 34;
        }
        absolute(list, padding, listTop, Math.max(1, contentWidth - padding * 2),
                Math.max(1, contentHeight - listTop - 7));
        int popupTop = stackedToolbar ? 58 : 31;
        int desiredPopupHeight = Math.max(54, availableCategories.size() * 23 + 28);
        int popupHeight = Math.max(1,
                Math.min(desiredPopupHeight, contentHeight - popupTop - padding));
        absolute(categoryFilterPopup, Math.max(padding, contentWidth - padding - filterWidth),
                popupTop, filterWidth, popupHeight);

        boolean nextCompactEditor = contentWidth < 250;
        if (compactEditor != nextCompactEditor) {
            compactEditor = nextCompactEditor;
            list.virtualScrollerViewStyle(style -> style
                    .estimatedItemHeight(compactEditor ? 51f : 33f)
                    .overscanPixels(compactEditor ? 102 : 66));
            list.refreshVisibleItems();
        }
    }

    private void layoutSidebar(int sidebarWidth, int sidebarHeight, boolean stacked) {
        if (stacked) {
            absolute(subtitleLabel, 0, 24, sidebarWidth, 14);
            absolute(pendingLabel, 0, 41, sidebarWidth, 17);
            int buttonGap = 5;
            int buttonTop = 64;
            int buttonWidth = Math.max(1, (sidebarWidth - buttonGap * 2) / 3);
            absolute(clearButton, 0, buttonTop, buttonWidth, 24);
            absolute(saveButton, buttonWidth + buttonGap, buttonTop, buttonWidth, 24);
            absolute(exitButton, (buttonWidth + buttonGap) * 2, buttonTop, buttonWidth, 24);
            return;
        }
        absolute(subtitleLabel, 0, 28, sidebarWidth, 16);
        absolute(pendingLabel, 0, 50, sidebarWidth, Math.max(24, sidebarHeight - 142));
        absolute(clearButton, 0, Math.max(0, sidebarHeight - 82), sidebarWidth, 22);
        absolute(saveButton, 0, Math.max(0, sidebarHeight - 55), sidebarWidth, 26);
        absolute(exitButton, 0, Math.max(0, sidebarHeight - 24), sidebarWidth, 24);
    }

    private static void absolute(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(left).top(top)
                .width(Math.max(1, width)).height(Math.max(1, height)));
    }

    private void applySetting(String settingName, String value) {
        FPSMatch.sendToServer(new MapRoomSettingsC2SPacket(
                detail.summary().gameType(), detail.summary().mapName(), settingName, value));
    }

    private boolean saveChanges() {
        if (pendingValues.isEmpty()) {
            return true;
        }
        if (saveInFlight || !detail.summary().currentPlayerOp() || !allPendingValuesValid()) {
            updatePendingState();
            return false;
        }
        List<Map.Entry<String, String>> changes = new ArrayList<>(pendingValues.entrySet());
        submittedChangeCount = changes.size();
        for (Map.Entry<String, String> change : changes) {
            applySetting(change.getKey(), change.getValue());
        }
        saveInFlight = true;
        saveTicks = 0;
        saveFailed = false;
        saveFailureMessage = null;
        updatePendingState();
        return false;
    }

    private void saveAndClose() {
        closeAfterSave = true;
        if (saveChanges()) {
            closeAfterSave = false;
            super.onClose();
        }
    }

    private void requestClose() {
        if (pendingValues.isEmpty()) {
            super.onClose();
            return;
        }
        discardConfirmation = true;
        categoryFilterPopup.setVisible(false);
        updatePendingState();
        announce(Component.translatable("gui.fpsm.map_select.settings.discard.message"), true);
    }

    private void cancelDiscard() {
        discardConfirmation = false;
        updatePendingState();
        accessibility().reconcileFocus();
    }

    private void discardAndClose() {
        pendingValues.clear();
        discardConfirmation = false;
        super.onClose();
    }

    @Override
    public void onClose() {
        if (saveInFlight) {
            return;
        }
        if (discardConfirmation) {
            cancelDiscard();
            return;
        }
        requestClose();
    }

    private void clearPendingChanges() {
        discardConfirmation = false;
        saveFailed = false;
        saveFailureMessage = null;
        pendingValues.clear();
        refreshContent();
    }

    private void stageSetting(MapRoomSettingInfo setting, String value) {
        discardConfirmation = false;
        saveFailed = false;
        saveFailureMessage = null;
        String staged = value == null ? "" : value;
        String current = detail.settings().stream()
                .filter(candidate -> candidate.name().equals(setting.name()))
                .map(MapRoomSettingInfo::value)
                .findFirst()
                .orElse(setting.value());
        if (Objects.equals(current, staged)) {
            pendingValues.remove(setting.name());
        } else {
            pendingValues.put(setting.name(), staged);
        }
        updatePendingState();
    }

    private String currentValue(MapRoomSettingInfo setting) {
        return pendingValues.getOrDefault(setting.name(), setting.value());
    }

    private boolean matchesSearch(MapRoomSettingInfo setting) {
        if (searchQuery.isEmpty()) {
            return true;
        }
        String query = searchQuery.toLowerCase(Locale.ROOT);
        String translated = Component.translatable(setting.translationKey()).getString()
                .toLowerCase(Locale.ROOT);
        String categoryKey = setting.categoryTranslationKey();
        String translatedCategory = Component.translatable(categoryKey).getString()
                .toLowerCase(Locale.ROOT);
        return setting.name().toLowerCase(Locale.ROOT).contains(query)
                || setting.translationKey().toLowerCase(Locale.ROOT).contains(query)
                || translated.contains(query)
                || setting.category().toLowerCase(Locale.ROOT).contains(query)
                || categoryKey.toLowerCase(Locale.ROOT).contains(query)
                || translatedCategory.contains(query);
    }

    private void prunePendingValues() {
        Map<String, String> current = new LinkedHashMap<>();
        detail.settings().forEach(setting -> current.put(setting.name(), setting.value()));
        pendingValues.entrySet().removeIf(entry -> !current.containsKey(entry.getKey())
                || Objects.equals(current.get(entry.getKey()), entry.getValue()));
    }

    private void updatePendingState() {
        boolean invalid = !allPendingValuesValid();
        if (discardConfirmation) {
            pendingLabel.setValue(Component.translatable(
                    "gui.fpsm.map_select.settings.discard.message"));
        } else if (saveInFlight) {
            pendingLabel.setValue(Component.translatable("gui.fpsm.map_select.settings.saving"));
        } else if (saveFailed) {
            pendingLabel.setValue(saveFailureMessage == null
                    ? Component.translatable("gui.fpsm.map_select.settings.save_failed")
                    : saveFailureMessage);
        } else if (pendingValues.isEmpty()) {
            pendingLabel.setValue(Component.translatable("gui.fpsm.map_select.settings.no_changes"));
        } else if (invalid) {
            pendingLabel.setValue(Component.translatable("gui.fpsm.map_select.settings.invalid"));
        } else {
            pendingLabel.setValue(Component.translatable("gui.fpsm.map_select.settings.pending", pendingValues.size()));
        }
        pendingLabel.textStyle(style -> style.fontSize(9).textColor(
                discardConfirmation || saveFailed
                        ? FPSMMapSelectTheme.DANGER
                        : invalid || saveInFlight
                                ? FPSMMapSelectTheme.WARNING
                                : FPSMMapSelectTheme.TEXT));

        boolean canClear = detail.summary().currentPlayerOp()
                && !pendingValues.isEmpty() && !saveInFlight && !discardConfirmation;
        clearButton.setVisible(!discardConfirmation);
        FPSMMapSelectTheme.buttonState(clearButton,
                FPSMMapSelectTheme.ButtonKind.QUIET, canClear);

        saveButton.setText(Component.translatable(discardConfirmation
                ? "gui.fpsm.map_select.settings.keep_editing"
                : "gui.fpsm.map_select.settings.save"));
        FPSMMapSelectTheme.buttonState(saveButton,
                discardConfirmation
                        ? FPSMMapSelectTheme.ButtonKind.SECONDARY
                        : FPSMMapSelectTheme.ButtonKind.PRIMARY,
                discardConfirmation || !saveInFlight
                        && detail.summary().currentPlayerOp()
                        && !pendingValues.isEmpty() && !invalid);

        exitButton.setText(Component.translatable(discardConfirmation
                ? "gui.fpsm.map_select.settings.discard"
                : "gui.back"));
        FPSMMapSelectTheme.buttonState(exitButton,
                discardConfirmation
                        ? FPSMMapSelectTheme.ButtonKind.DANGER
                        : FPSMMapSelectTheme.ButtonKind.QUIET,
                !saveInFlight);
    }

    private boolean allPendingValuesValid() {
        return pendingValues.entrySet().stream().allMatch(entry -> detail.settings().stream()
                .filter(setting -> setting.name().equals(entry.getKey()))
                .findFirst()
                .map(setting -> validValue(setting, entry.getValue()))
                .orElse(false));
    }

    private static boolean validValue(MapRoomSettingInfo setting, String value) {
        if (value == null || value.length() > 1024) {
            return false;
        }
        try {
            return switch (setting.type()) {
                case BOOLEAN -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
                case INTEGER -> {
                    Long.parseLong(value);
                    yield true;
                }
                case DECIMAL -> Double.isFinite(Double.parseDouble(value));
                default -> true;
            };
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Parts build() {
        UIElement root = new UIElement().setId("fpsmatch.map_settings.root");
        root.layout(layout -> layout.widthPercent(100).heightPercent(100));
        FPSMMapSelectTheme.root(root);

        UIElement sidebar = new UIElement().setId("fpsmatch.map_settings.sidebar");
        sidebar.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(14).top(12).bottom(12).width(112));

        Label header = label("fpsmatch.map_settings.header", Component.translatable("gui.fpsm.map_select.settings.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(0).right(0).top(0).height(22));
        FPSMMapSelectTheme.title(header);
        header.textStyle(style -> style.fontSize(16).textWrap(TextWrap.HIDE));

        Label subtitle = label("fpsmatch.map_settings.subtitle", Component.empty());
        subtitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(0).right(0).top(22).height(14));
        FPSMMapSelectTheme.mapIdentity(subtitle);
        subtitle.textStyle(style -> style.fontSize(9).textWrap(TextWrap.HIDE));

        Label pending = label("fpsmatch.map_settings.pending", Component.empty());
        pending.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(0).right(0).top(40).height(28));
        FPSMMapSelectTheme.muted(pending);
        pending.textStyle(style -> style.fontSize(9).textWrap(TextWrap.WRAP));

        AccessibleButton clear = new AccessibleButton();
        clear.setId("fpsmatch.map_settings.clear_changes");
        clear.setText(Component.translatable("gui.fpsm.map_select.settings.clear_changes"));
        clear.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(0).right(0).bottom(22).height(18));
        FPSMMapSelectTheme.button(clear, FPSMMapSelectTheme.ButtonKind.QUIET);
        clear.textStyle(style -> style.fontSize(9));
        clear.setActive(false);

        AccessibleButton save = new AccessibleButton();
        save.setId("fpsmatch.map_settings.save");
        save.setText(Component.translatable("gui.fpsm.map_select.settings.save"));
        FPSMMapSelectTheme.button(save, FPSMMapSelectTheme.ButtonKind.PRIMARY);
        save.textStyle(style -> style.fontSize(10));
        save.setActive(false);

        AccessibleButton exit = new AccessibleButton();
        exit.setId("fpsmatch.map_settings.exit");
        exit.setText(Component.translatable("gui.back"));
        exit.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(0).right(0).bottom(0).height(18));
        FPSMMapSelectTheme.button(exit, FPSMMapSelectTheme.ButtonKind.QUIET);
        exit.textStyle(style -> style.fontSize(9));

        sidebar.addChildren(header, subtitle, pending, clear, save, exit);

        UIElement panel = new UIElement().setId("fpsmatch.map_settings.panel");
        panel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(134).right(14).top(12).bottom(12));
        FPSMMapSelectTheme.panel(panel);

        TextField search = new TextField();
        search.setId("fpsmatch.map_settings.search");
        search.setAnyString();
        search.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(6).top(5).width(100).height(18));
        FPSMMapSelectTheme.input(search, Component.translatable("gui.fpsm.map_select.settings.search"));
        search.textFieldStyle(style -> style.fontSize(10));

        AccessibleButton categoryFilter = new AccessibleButton();
        categoryFilter.setId("fpsmatch.map_settings.category_filter");
        categoryFilter.setText(Component.translatable("gui.fpsm.map_select.settings.category_filter"));
        categoryFilter.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(6).top(5).width(88).height(18));
        FPSMMapSelectTheme.button(categoryFilter, FPSMMapSelectTheme.ButtonKind.SECONDARY);
        categoryFilter.textStyle(style -> style.fontSize(9));
        categoryFilter.style(style -> style.tooltips(Component.translatable(
                "gui.fpsm.map_select.settings.category_filter.tooltip")));

        Label empty = label("fpsmatch.map_settings.empty", Component.translatable("gui.fpsm.map_select.settings.empty"));
        empty.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(10).right(10).top(34).height(16));
        FPSMMapSelectTheme.muted(empty);
        empty.textStyle(style -> style.fontSize(10));
        empty.setVisible(false);

        VirtualScrollerView<SettingListEntry> list = new VirtualScrollerView<>();
        list.setId("fpsmatch.map_settings.list");
        list.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(6).right(6).top(27).bottom(6));
        list.virtualScrollerViewStyle(style -> style.estimatedItemHeight(33f).overscanPixels(66));
        FPSMMapSelectTheme.virtualScroller(list);

        UIElement categoryPopup = new UIElement().setId("fpsmatch.map_settings.category_filter.popup");
        categoryPopup.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(6).top(26).width(88).height(96).paddingAll(3));
        categoryPopup.style(style -> style.zIndex(20));
        FPSMMapSelectTheme.elevated(categoryPopup);
        categoryPopup.setVisible(false);

        VirtualScrollerView<String> categoryList = new VirtualScrollerView<>();
        categoryList.setId("fpsmatch.map_settings.category_filter.list");
        categoryList.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(3).right(3).top(3).bottom(19));
        categoryList.virtualScrollerViewStyle(style -> style.estimatedItemHeight(23f).overscanPixels(46));
        FPSMMapSelectTheme.virtualScroller(categoryList);

        AccessibleButton clearCategorySelection = new AccessibleButton();
        clearCategorySelection.setId("fpsmatch.map_settings.category_filter.clear");
        clearCategorySelection.setText(Component.translatable(
                "gui.fpsm.map_select.settings.category_filter.clear"));
        clearCategorySelection.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(3).right(3).bottom(3).height(14));
        FPSMMapSelectTheme.button(clearCategorySelection, FPSMMapSelectTheme.ButtonKind.QUIET);
        clearCategorySelection.textStyle(style -> style.fontSize(9));
        clearCategorySelection.setActive(false);

        categoryPopup.addChildren(categoryList, clearCategorySelection);

        panel.addChildren(search, categoryFilter, empty, list, categoryPopup);
        root.addChildren(sidebar, panel);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))),
                sidebar, panel, subtitle, pending, search, categoryFilter, categoryPopup,
                categoryList, clearCategorySelection, list, empty, clear, save, exit);
    }

    private UIElement listRow(SettingListEntry entry) {
        if (entry instanceof CategoryEntry category) {
            return categoryRow(category.category());
        }
        return settingRow(((SettingEntry) entry).setting());
    }

    private UIElement categoryRow(String category) {
        UIElement row = new UIElement().setId("fpsmatch.map_settings.category." + category);
        row.layout(layout -> layout.widthPercent(100).height(22).marginTop(3).marginBottom(2));
        FPSMMapSelectTheme.settingsCategory(row);

        Label title = label("fpsmatch.map_settings.category.label." + category,
                Component.translatable(MapRoomSettingInfo.categoryTranslationKey(category)));
        title.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(8).right(8).top(5).height(12));
        FPSMMapSelectTheme.status(title, FPSMMapSelectTheme.SETTINGS_CATEGORY_TEXT);
        title.textStyle(style -> style.fontSize(10).textWrap(TextWrap.HIDE));
        row.addChild(title);
        return row;
    }

    private UIElement settingRow(MapRoomSettingInfo setting) {
        UIElement row = new UIElement().setId("fpsmatch.map_settings.row." + setting.name());
        row.layout(layout -> layout.widthPercent(100)
                .height(compactEditor ? 48 : 30).marginBottom(3));
        addSettingTooltip(row, setting);

        UIElement content = new UIElement().setId("fpsmatch.map_settings.row.content." + setting.name());
        content.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(2).right(2).top(0).bottom(0));
        FPSMMapSelectTheme.settingsEntry(content);
        addSettingTooltip(content, setting);
        row.addChild(content);

        Label name = label("fpsmatch.map_settings.name." + setting.name(), Component.translatable(setting.translationKey()));
        if (compactEditor) {
            name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(8).right(8).top(5).height(12));
        } else {
            name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(8).top(9).widthPercent(34).height(12));
        }
        FPSMMapSelectTheme.status(name, FPSMMapSelectTheme.SETTINGS_ENTRY_TEXT);
        name.textStyle(style -> style.fontSize(9).textWrap(TextWrap.HIDE));
        addSettingTooltip(name, setting);
        content.addChild(name);

        if (setting.type() == MapRoomSettingInfo.SettingType.BOOLEAN) {
            boolean[] value = {Boolean.parseBoolean(currentValue(setting))};
            AccessibleButton toggle = new AccessibleButton();
            toggle.setId("fpsmatch.map_settings.toggle." + setting.name());
            toggle.setText(toggleLabel(value[0]));
            if (compactEditor) {
                toggle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                        .left(8).right(8).top(22).height(20));
            } else {
                toggle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                        .right(6).top(5).width(72).height(20));
            }
            FPSMMapSelectTheme.button(toggle, FPSMMapSelectTheme.ButtonKind.SECONDARY);
            toggle.textStyle(style -> style.fontSize(9));
            toggle.setAccessibleName(Component.translatable(setting.translationKey()));
            toggle.setAccessibleState(() -> toggleLabel(value[0]));
            addSettingTooltip(toggle, setting);
            toggle.setActive(setting.editable());
            toggle.setOnClick(e -> {
                value[0] = !value[0];
                toggle.setText(toggleLabel(value[0]));
                stageSetting(setting, String.valueOf(value[0]));
            });
            content.addChild(toggle);
        } else if (setting.slider() && validSlider(setting)) {
            addSliderEditor(content, setting);
        } else {
            addTextEditor(content, setting);
        }
        return row;
    }

    private void addSliderEditor(UIElement row, MapRoomSettingInfo setting) {
        float min = (float) setting.minValue();
        float max = (float) setting.maxValue();
        float current = clamp(parseFloat(currentValue(setting), min), min, max);
        SteppedSlider slider = new SteppedSlider(min, max, (float) setting.step());
        slider.setId("fpsmatch.map_settings.slider." + setting.name());
        slider.setValue(current, false);
        slider.setScrollBarSize(14);
        slider.scrollerStyle(style -> style.scrollDelta(slider.normalizedStep()));
        if (compactEditor) {
            slider.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(8).right(60).top(31).height(5));
        } else {
            slider.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .leftPercent(40).right(58).top(13).height(5));
        }
        slider.scrollContainer(element -> element.style(style -> style.background(
                FPSMMapSelectTheme.panelTexture(0xFF10161D, FPSMMapSelectTheme.BORDER))));
        slider.scrollBar(button -> FPSMMapSelectTheme.button(button, FPSMMapSelectTheme.ButtonKind.SECONDARY));
        slider.setActive(setting.editable());
        addSettingTooltip(slider, setting);

        Label value = label("fpsmatch.map_settings.slider.value." + setting.name(),
                Component.literal(formatNumber(current, setting.type())));
        value.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(8).top(compactEditor ? 25 : 9).width(46).height(12));
        FPSMMapSelectTheme.status(value, FPSMMapSelectTheme.SETTINGS_ENTRY_TEXT);
        value.textStyle(style -> style.fontSize(9));
        addSettingTooltip(value, setting);
        slider.setOnValueChanged(changed -> {
            String formatted = formatNumber(changed, setting.type());
            value.setValue(Component.literal(formatted));
            stageSetting(setting, formatted);
        });
        row.addChildren(slider, value);
    }

    private void addTextEditor(UIElement row, MapRoomSettingInfo setting) {
        TextField field = new TextField();
        field.setId("fpsmatch.map_settings.field." + setting.name());
        switch (setting.type()) {
            case INTEGER -> field.setNumbersOnlyLong(Long.MIN_VALUE, Long.MAX_VALUE);
            case DECIMAL -> field.setNumbersOnlyDouble(-Double.MAX_VALUE, Double.MAX_VALUE);
            default -> field.setAnyString().setTextValidator(value -> value.length() <= 1024);
        }
        field.setText(currentValue(setting));
        if (compactEditor) {
            field.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(8).right(8).top(22).height(20));
        } else {
            field.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .leftPercent(40).right(6).top(5).height(20));
        }
        FPSMMapSelectTheme.input(field, Component.literal(setting.defaultValue()));
        field.textFieldStyle(style -> style.fontSize(9));
        field.setActive(setting.editable());
        addSettingTooltip(field, setting);
        field.setTextResponder(value -> stageSetting(setting, value));
        row.addChild(field);
    }

    private static boolean validSlider(MapRoomSettingInfo setting) {
        return Double.isFinite(setting.minValue()) && Double.isFinite(setting.maxValue())
                && Double.isFinite(setting.step()) && setting.maxValue() > setting.minValue()
                && setting.step() > 0.0;
    }

    private static String formatNumber(float value, MapRoomSettingInfo.SettingType type) {
        if (type == MapRoomSettingInfo.SettingType.INTEGER) {
            return Long.toString(Math.round(value));
        }
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void addSettingTooltip(UIElement element, MapRoomSettingInfo setting) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable(setting.descriptionKey()));
        lines.add(Component.translatable("gui.fpsm.map_select.setting.default", setting.defaultValue()));
        if (setting.slider() && validSlider(setting)) {
            lines.add(Component.translatable("gui.fpsm.map_select.setting.range",
                    formatNumber((float) setting.minValue(), setting.type()),
                    formatNumber((float) setting.maxValue(), setting.type()),
                    formatNumber((float) setting.step(), setting.type())));
        }
        element.style(style -> style.tooltips(lines.toArray(Component[]::new)));
    }

    private static Component toggleLabel(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }

    private static final class SteppedSlider extends Scroller.Horizontal {
        private final float step;

        private SteppedSlider(float min, float max, float step) {
            this.step = step;
            setRange(min, max);
        }

        private float normalizedStep() {
            return step / (getMaxValue() - getMinValue());
        }

        @Override
        public Scroller setValue(Float value, boolean notify) {
            if (value == null || !Float.isFinite(value) || step <= 0f) {
                return super.setValue(value, notify);
            }
            float snapped = getMinValue()
                    + Math.round((value - getMinValue()) / step) * step;
            return super.setValue(clamp(snapped, getMinValue(), getMaxValue()), notify);
        }
    }

    private static Label label(String id, Component text) {
        Label label = new Label();
        label.setId(id);
        label.setValue(text);
        return label;
    }

    private sealed interface SettingListEntry permits CategoryEntry, SettingEntry {
    }

    private record CategoryEntry(String category) implements SettingListEntry {
    }

    private record SettingEntry(MapRoomSettingInfo setting) implements SettingListEntry {
    }

    private record Parts(ModularUI ui, UIElement sidebar, UIElement panel, Label subtitle,
                         Label pending, TextField search, AccessibleButton categoryFilterButton,
                         UIElement categoryFilterPopup, VirtualScrollerView<String> categoryFilterList,
                         AccessibleButton clearCategorySelectionButton, VirtualScrollerView<SettingListEntry> list,
                         Label empty, AccessibleButton clearButton, AccessibleButton saveButton,
                         AccessibleButton exitButton) {}
}
