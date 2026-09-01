package com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.VirtualScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.math.Size;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.screen.ldlib2.FPSMLdlib2Theme;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSettingInfo;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSettingsC2SPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomToastS2CPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.Util;
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
    private final Button categoryFilterButton;
    private final UIElement categoryFilterPopup;
    private final VirtualScrollerView<String> categoryFilterList;
    private final Button clearCategorySelectionButton;
    private final VirtualScrollerView<SettingListEntry> list;
    private final Label emptyLabel;
    private final HoldToClearButton clearButton;
    private final Button exitButton;
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
        this.clearButton.setOnHoldComplete(this::clearPendingChanges);
        this.exitButton.setOnClick(e -> saveAndClose());
        refreshContent();
    }

    @Override
    public void init() {
        super.init();
        applyResponsiveLayout();
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
    }

    @Override
    protected void onDetailApplied() {
        if (!detail.summary().currentPlayerOp()) {
            pendingValues.clear();
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
        Toggle toggle = new Toggle();
        toggle.setId("fpsmatch.map_settings.category_filter.option." + category);
        toggle.setText(Component.translatable(MapRoomSettingInfo.categoryTranslationKey(category)));
        toggle.setOn(selectedCategories.contains(category), false);
        toggle.layout(layout -> layout.widthPercent(100).height(14).marginBottom(2));
        FPSMLdlib2Theme.settingsCategoryToggle(toggle);
        toggle.style(style -> style.tooltips(Component.translatable(
                MapRoomSettingInfo.categoryTranslationKey(category))));
        toggle.setOnToggleChanged(selected -> {
            if (selected) {
                selectedCategories.add(category);
            } else {
                selectedCategories.remove(category);
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
        MapSettingsLayoutModel model = MapSettingsLayoutModel.responsive(width, height);
        layoutRect(sidebar, model.sidebar());
        layoutRect(panel, model.content());
        MapSettingsLayoutModel.ToolbarLayout toolbar = MapSettingsLayoutModel.toolbar(
                model.content().width(), model.content().height(), availableCategories.size());
        layoutRect(searchField, toolbar.search());
        layoutRect(categoryFilterButton, toolbar.categoryFilter());
        layoutRect(list, toolbar.list());
        layoutRect(categoryFilterPopup, toolbar.categoryPopup());
    }

    private static void layoutRect(UIElement element, MapSettingsLayoutModel.Rect rect) {
        element.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .rightAuto().bottomAuto().left(rect.x()).top(rect.y())
                .width(rect.width()).height(rect.height()));
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

    @Override
    public void onClose() {
        if (saveInFlight) {
            closeAfterSave = false;
            return;
        }
        saveAndClose();
    }

    private void clearPendingChanges() {
        pendingValues.clear();
        refreshContent();
    }

    private void stageSetting(MapRoomSettingInfo setting, String value) {
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
        if (saveInFlight) {
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
        pendingLabel.textStyle(style -> style.textColor(saveFailed
                ? FPSMLdlib2Theme.DANGER
                : invalid || saveInFlight ? FPSMLdlib2Theme.WARNING : FPSMLdlib2Theme.TEXT));
        boolean canClear = detail.summary().currentPlayerOp() && !pendingValues.isEmpty() && !saveInFlight;
        clearButton.setActive(canClear);
        FPSMLdlib2Theme.holdActionButton(clearButton, canClear);
        clearButton.textStyle(style -> style.fontSize(7));
        exitButton.setActive(!saveInFlight && (pendingValues.isEmpty()
                || detail.summary().currentPlayerOp() && !invalid));
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
        FPSMLdlib2Theme.root(root);

        Label system = label("fpsmatch.map_settings.system", Component.literal("FPSM // MAP SYSTEM  ·  CONFIGURATION GRID"));
        system.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(14).right(14).top(2).height(9));
        FPSMLdlib2Theme.systemLabel(system);

        UIElement sidebar = new UIElement().setId("fpsmatch.map_settings.sidebar");
        sidebar.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(14).top(12).bottom(12).width(112));

        Label header = label("fpsmatch.map_settings.header", Component.translatable("gui.fpsm.map_select.settings.title"));
        header.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(0).right(0).top(0).height(18));
        FPSMLdlib2Theme.title(header);
        header.textStyle(style -> style.fontSize(12).textWrap(TextWrap.HIDE));

        Label subtitle = label("fpsmatch.map_settings.subtitle", Component.empty());
        subtitle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(0).right(0).top(22).height(14));
        FPSMLdlib2Theme.mapIdentity(subtitle);
        subtitle.textStyle(style -> style.fontSize(8).textWrap(TextWrap.HIDE));

        Label pending = label("fpsmatch.map_settings.pending", Component.empty());
        pending.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(0).right(0).top(40).height(28));
        FPSMLdlib2Theme.muted(pending);
        pending.textStyle(style -> style.fontSize(8).textWrap(TextWrap.WRAP));

        HoldToClearButton clear = new HoldToClearButton();
        clear.setId("fpsmatch.map_settings.clear_changes");
        clear.setText(Component.translatable("gui.fpsm.map_select.settings.clear_changes"));
        clear.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(0).right(0).bottom(22).height(18));
        FPSMLdlib2Theme.holdActionButton(clear, false);
        clear.textStyle(style -> style.fontSize(7));
        clear.style(style -> style.tooltips(Component.translatable(
                "gui.fpsm.map_select.settings.clear_changes.hold")));
        clear.setActive(false);

        Button exit = new Button();
        exit.setId("fpsmatch.map_settings.exit");
        exit.setText(Component.translatable("gui.fpsm.map_select.settings.exit"));
        exit.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(0).right(0).bottom(0).height(18));
        FPSMLdlib2Theme.button(exit, FPSMLdlib2Theme.ButtonKind.QUIET);
        exit.textStyle(style -> style.fontSize(7));
        exit.style(style -> style.tooltips(Component.translatable(
                "gui.fpsm.map_select.settings.exit.tooltip")));

        sidebar.addChildren(header, subtitle, pending, clear, exit);

        UIElement panel = new UIElement().setId("fpsmatch.map_settings.panel");
        panel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(134).right(14).top(12).bottom(12));
        FPSMLdlib2Theme.panel(panel);

        TextField search = new TextField();
        search.setId("fpsmatch.map_settings.search");
        search.setAnyString();
        search.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(6).top(5).width(100).height(18));
        FPSMLdlib2Theme.input(search, Component.translatable("gui.fpsm.map_select.settings.search"));
        search.textFieldStyle(style -> style.fontSize(8));

        Button categoryFilter = new Button();
        categoryFilter.setId("fpsmatch.map_settings.category_filter");
        categoryFilter.setText(Component.translatable("gui.fpsm.map_select.settings.category_filter"));
        categoryFilter.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(6).top(5).width(88).height(18));
        FPSMLdlib2Theme.button(categoryFilter, FPSMLdlib2Theme.ButtonKind.SECONDARY);
        categoryFilter.textStyle(style -> style.fontSize(7));
        categoryFilter.style(style -> style.tooltips(Component.translatable(
                "gui.fpsm.map_select.settings.category_filter.tooltip")));

        Label empty = label("fpsmatch.map_settings.empty", Component.translatable("gui.fpsm.map_select.settings.empty"));
        empty.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE).left(10).right(10).top(34).height(16));
        FPSMLdlib2Theme.muted(empty);
        empty.textStyle(style -> style.fontSize(8));
        empty.setVisible(false);

        VirtualScrollerView<SettingListEntry> list = new VirtualScrollerView<>();
        list.setId("fpsmatch.map_settings.list");
        list.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(6).right(6).top(27).bottom(6));
        list.virtualScrollerViewStyle(style -> style.estimatedItemHeight(21f).overscanPixels(42));
        FPSMLdlib2Theme.virtualScroller(list);

        UIElement categoryPopup = new UIElement().setId("fpsmatch.map_settings.category_filter.popup");
        categoryPopup.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(6).top(26).width(88).height(96).paddingAll(3));
        categoryPopup.style(style -> style.zIndex(20));
        FPSMLdlib2Theme.elevated(categoryPopup);
        categoryPopup.setVisible(false);

        VirtualScrollerView<String> categoryList = new VirtualScrollerView<>();
        categoryList.setId("fpsmatch.map_settings.category_filter.list");
        categoryList.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(3).right(3).top(3).bottom(19));
        categoryList.virtualScrollerViewStyle(style -> style.estimatedItemHeight(16f).overscanPixels(32));
        FPSMLdlib2Theme.virtualScroller(categoryList);

        Button clearCategorySelection = new Button();
        clearCategorySelection.setId("fpsmatch.map_settings.category_filter.clear");
        clearCategorySelection.setText(Component.translatable(
                "gui.fpsm.map_select.settings.category_filter.clear"));
        clearCategorySelection.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(3).right(3).bottom(3).height(14));
        FPSMLdlib2Theme.button(clearCategorySelection, FPSMLdlib2Theme.ButtonKind.QUIET);
        clearCategorySelection.textStyle(style -> style.fontSize(7));
        clearCategorySelection.setActive(false);

        categoryPopup.addChildren(categoryList, clearCategorySelection);

        panel.addChildren(search, categoryFilter, empty, list, categoryPopup);
        root.addChildren(system, sidebar, panel);
        return new Parts(ModularUI.of(UI.of(root, size -> Size.of(size.getWidth(), size.getHeight()))),
                sidebar, panel, subtitle, pending, search, categoryFilter, categoryPopup,
                categoryList, clearCategorySelection, list, empty, clear, exit);
    }

    private UIElement listRow(SettingListEntry entry) {
        if (entry instanceof CategoryEntry category) {
            return categoryRow(category.category());
        }
        return settingRow(((SettingEntry) entry).setting());
    }

    private UIElement categoryRow(String category) {
        UIElement row = new UIElement().setId("fpsmatch.map_settings.category." + category);
        row.layout(layout -> layout.widthPercent(100).height(16).marginTop(2).marginBottom(1));
        FPSMLdlib2Theme.settingsCategory(row);

        Label title = label("fpsmatch.map_settings.category.label." + category,
                Component.translatable(MapRoomSettingInfo.categoryTranslationKey(category)));
        title.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(6).right(6).top(3).height(9));
        FPSMLdlib2Theme.status(title, FPSMLdlib2Theme.SETTINGS_CATEGORY_TEXT);
        title.textStyle(style -> style.fontSize(8).textWrap(TextWrap.HIDE));
        row.addChild(title);
        return row;
    }

    private UIElement settingRow(MapRoomSettingInfo setting) {
        UIElement row = new UIElement().setId("fpsmatch.map_settings.row." + setting.name());
        row.layout(layout -> layout.widthPercent(100).height(20).marginBottom(1));
        addSettingTooltip(row, setting);

        UIElement content = new UIElement().setId("fpsmatch.map_settings.row.content." + setting.name());
        content.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(5).right(1).top(0).bottom(1));
        FPSMLdlib2Theme.settingsEntry(content);
        addSettingTooltip(content, setting);
        row.addChild(content);

        Label name = label("fpsmatch.map_settings.name." + setting.name(), Component.translatable(setting.translationKey()));
        name.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .left(6).top(5).widthPercent(31).height(10));
        FPSMLdlib2Theme.status(name, FPSMLdlib2Theme.SETTINGS_ENTRY_TEXT);
        name.textStyle(style -> style.fontSize(7).textWrap(TextWrap.HIDE));
        addSettingTooltip(name, setting);
        content.addChild(name);

        if (setting.type() == MapRoomSettingInfo.SettingType.BOOLEAN) {
            boolean[] value = {Boolean.parseBoolean(currentValue(setting))};
            Button toggle = new Button();
            toggle.setId("fpsmatch.map_settings.toggle." + setting.name());
            toggle.setText(toggleLabel(value[0]));
            toggle.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .right(3).top(2).width(56).height(14));
            FPSMLdlib2Theme.button(toggle, FPSMLdlib2Theme.ButtonKind.SECONDARY);
            toggle.textStyle(style -> style.fontSize(7));
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
        slider.setScrollBarSize(10);
        slider.scrollerStyle(style -> style.scrollDelta(slider.normalizedStep()));
        slider.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .leftPercent(38).right(44).top(8).height(4));
        slider.scrollContainer(element -> element.style(style -> style.background(
                FPSMLdlib2Theme.panelTexture(0xFF10161D, FPSMLdlib2Theme.BORDER))));
        slider.scrollBar(button -> FPSMLdlib2Theme.button(button, FPSMLdlib2Theme.ButtonKind.SECONDARY));
        slider.setActive(setting.editable());
        addSettingTooltip(slider, setting);

        Label value = label("fpsmatch.map_settings.slider.value." + setting.name(),
                Component.literal(formatNumber(current, setting.type())));
        value.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .right(3).top(4).width(36).height(10));
        FPSMLdlib2Theme.status(value, FPSMLdlib2Theme.SETTINGS_ENTRY_TEXT);
        value.textStyle(style -> style.fontSize(7));
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
        field.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                .leftPercent(38).right(3).top(2).height(14));
        FPSMLdlib2Theme.input(field, Component.literal(setting.defaultValue()));
        field.textFieldStyle(style -> style.fontSize(7));
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

    /** A button whose action is intentionally difficult to trigger accidentally. */
    private static final class HoldToClearButton extends Button {
        private final HoldActionProgress holdProgress = new HoldActionProgress(900, 150, 220);
        private Runnable onHoldComplete = () -> {
        };

        private void setOnHoldComplete(Runnable action) {
            onHoldComplete = Objects.requireNonNull(action, "action");
        }

        @Override
        protected void onMouseDown(UIEvent event) {
            super.onMouseDown(event);
            if (event.button == 0 && isActive()) {
                holdProgress.press(Util.getMillis());
            }
        }

        @Override
        protected void onMouseUp(UIEvent event) {
            if (event.button == 0) {
                completeIfReady(Util.getMillis());
                holdProgress.release(Util.getMillis());
            }
            super.onMouseUp(event);
        }

        @Override
        protected void onMouseLeave(UIEvent event) {
            if (holdProgress.isHolding()) {
                completeIfReady(Util.getMillis());
                holdProgress.release(Util.getMillis());
            }
            super.onMouseLeave(event);
        }

        @Override
        public void screenTick() {
            super.screenTick();
            long now = Util.getMillis();
            if (!isActive() && holdProgress.isHolding()) {
                holdProgress.release(now);
            }
            completeIfReady(now);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            float progress = holdProgress.progress(Util.getMillis());
            float opacity = holdProgress.opacity(Util.getMillis());
            if (progress <= 0f || opacity <= 0f || getSizeWidth() <= 0 || getSizeHeight() <= 0) {
                return;
            }
            int alpha = Math.round(0x78 * opacity);
            int color = (alpha << 24) | (FPSMLdlib2Theme.HOLD_ACTION_PROGRESS & 0x00FFFFFF);
            int left = Math.round(getPositionX());
            int top = Math.round(getPositionY());
            int right = left + Math.max(1, Math.round(getSizeWidth() * progress));
            int bottom = top + Math.round(getSizeHeight());
            context.graphics.fill(left, top, right, bottom, color);
        }

        private void completeIfReady(long now) {
            if (holdProgress.update(now)) {
                onHoldComplete.run();
            }
        }
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
                         Label pending, TextField search, Button categoryFilterButton,
                         UIElement categoryFilterPopup, VirtualScrollerView<String> categoryFilterList,
                         Button clearCategorySelectionButton, VirtualScrollerView<SettingListEntry> list,
                         Label empty, HoldToClearButton clearButton, Button exitButton) {}
}
