package com.tacz.guns.client.gui;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.components.FlatColorButton;
import com.tacz.guns.client.gui.components.GunPackList;
import com.tacz.guns.client.gui.components.TextureImageButton;
import com.tacz.guns.client.gui.components.smith.ResultButton;
import com.tacz.guns.client.gui.components.smith.TypeButton;
import com.tacz.guns.client.recipe.ClientGunSmithRecipeRepository;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageCraft;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.util.GunSmithTableBlockIds;
import com.tacz.guns.util.MinecraftGuiCompat;
import com.tacz.guns.util.RenderDistance;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2fStack;

import javax.annotation.Nullable;
import java.util.*;

public class GunSmithTableScreen extends AbstractContainerScreen<GunSmithTableMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/gun_smith_table.png");
    private static final Identifier SIDE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/gun_smith_table_side.png");
    private static final Set<Identifier> WARNED_MISSING_BLOCK_INDEX = new HashSet<>();
    private static final Set<Identifier> WARNED_NETWORK_BLOCK_FALLBACK = new HashSet<>();
    private static final Set<String> LOGGED_RECIPE_SOURCE_DIAGNOSTICS = new HashSet<>();
    private static final Set<Identifier> WARNED_EMPTY_INGREDIENTS = new HashSet<>();
    private static final Set<Identifier> WARNED_TRUNCATED_INGREDIENTS = new HashSet<>();
    private static final int EMPTY_RECIPE_REFRESH_INTERVAL = 20;
    private static final int EMPTY_RECIPE_REFRESH_ATTEMPTS = 10;
    private static final int INGREDIENT_COLUMNS = 2;
    private static final int INGREDIENT_ROWS = 6;
    private static final int INGREDIENT_SLOT_SIZE = 16;
    private static final int MAX_RENDERED_INGREDIENTS = INGREDIENT_COLUMNS * INGREDIENT_ROWS;

    private final LinkedHashMap<Identifier, TabConfig> recipeKeys = Maps.newLinkedHashMap();
    private final Map<Identifier, List<Identifier>> recipes = Maps.newLinkedHashMap();
    private Map<Identifier, GunSmithTableRecipe> recipeView = Map.of();

    private int typePage;
    private Identifier selectedType = null;
    private List<Identifier> selectedRecipeList = new ArrayList<>();

    private int indexPage;
    private @Nullable GunSmithTableRecipe selectedRecipe;
    private @Nullable Identifier selectedRecipeId;
    private @Nullable Int2IntArrayMap playerIngredientCount;

    private int scale = 70;
    private boolean filterEnabled = false;
    private GunPackList filterList;
    private boolean autoByHandFilterApplied = false;
    private int emptyRecipeRefreshTicks;
    private int emptyRecipeRefreshAttempts;

    public GunSmithTableScreen(GunSmithTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 344, 186);
        this.classifyRecipes();
        this.typePage = 0;
        this.indexPage = 0;
        this.selectRecipe(selectedRecipeList != null && !this.selectedRecipeList.isEmpty() ? this.selectedRecipeList.get(0) : null);
        this.getPlayerIngredientCount(this.selectedRecipe);
    }

    public static void drawModCenteredString(GuiGraphicsExtractor gui, Font font, Component component, int pX, int pY, int color) {
        FormattedCharSequence text = component.getVisualOrderText();
        gui.text(font, text, pX - font.width(text) / 2, pY, color, false);
    }

    private void classifyRecipes() {
        this.recipes.clear();
        this.recipeKeys.clear();
        this.recipeView = Map.of();
        Identifier blockId = getNormalizedBlockId();
        if (blockId == null) {
            return;
        }
        Map<Identifier, List<Identifier>> recipes = Maps.newLinkedHashMap();
        Map<Identifier, TabConfig> recipeKeys = Maps.newLinkedHashMap();

        getDisplayBlockIndex(blockId).ifPresent(blockIndex -> {
            var tabs = blockIndex.getData().getTabs();
            if (DefaultAssets.DEFAULT_BLOCK_ID.equals(blockId) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
                tabs = TabConfig.DEFAULT_TABS;
            }
            for (TabConfig tab : tabs) {
                recipes.put(tab.id(), Lists.newArrayList());
                recipeKeys.put(tab.id(), tab);
            }
        });

        SourceEvaluation sourceEvaluation = selectRecipeSource(recipeKeys);
        this.recipeView = sourceEvaluation.recipeView();

        sourceEvaluation.recipeIds().forEach(entry -> {
            Identifier groupName = entry.key();
            if (recipeKeys.containsKey(groupName)) {
                recipes.computeIfAbsent(groupName, g -> Lists.newArrayList()).add(entry.value());
            }
        });

        for (var entry : recipes.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                this.recipes.put(entry.getKey(), entry.getValue());
                this.recipeKeys.put(entry.getKey(), recipeKeys.get(entry.getKey()));
            }
        }

        if (!this.recipeKeys.containsKey(selectedType)) {
            selectedType = null;
            selectedRecipeList = null;
            indexPage = 0;
        }

        if (!this.recipeKeys.keySet().isEmpty()) {
            if (selectedType == null) {
                selectedType = this.recipeKeys.keySet().iterator().next();
            }
        }

        if (selectedType != null) {
            selectedRecipeList = this.recipes.get(selectedType);
        }
    }

    private SourceEvaluation selectRecipeSource(Map<Identifier, TabConfig> recipeKeys) {
        if (recipeKeys.isEmpty()) {
            return SourceEvaluation.empty();
        }
        SourceCandidate bestCandidate = null;
        List<SourceCandidate> candidates = new ArrayList<>();
        for (ClientGunSmithRecipeRepository.Source source : ClientGunSmithRecipeRepository.getSources()) {
            SourceDiagnostics diagnostics = diagnoseRecipeSource(source.recipes(), recipeKeys);
            SourceEvaluation evaluation = evaluateRecipeSource(source, recipeKeys);
            SourceCandidate candidate = new SourceCandidate(source, diagnostics, evaluation);
            candidates.add(candidate);
            if (isBetterRecipeSource(candidate, bestCandidate)) {
                bestCandidate = candidate;
            }
        }
        for (SourceCandidate candidate : candidates) {
            logRecipeSourceDiagnostics(candidate.source(), candidate.diagnostics(), candidate.evaluation().recipeIds().size(), candidate == bestCandidate);
        }
        return bestCandidate == null ? SourceEvaluation.empty() : bestCandidate.evaluation();
    }

    private static boolean isBetterRecipeSource(@NotNull SourceCandidate candidate, @Nullable SourceCandidate currentBest) {
        if (currentBest == null) {
            return true;
        }
        int candidateVisible = candidate.evaluation().recipeIds().size();
        int bestVisible = currentBest.evaluation().recipeIds().size();
        if (candidateVisible != bestVisible) {
            return candidateVisible > bestVisible;
        }
        return candidate.diagnostics().classified() > currentBest.diagnostics().classified();
    }

    private SourceEvaluation evaluateRecipeSource(ClientGunSmithRecipeRepository.Source source,
                                                  Map<Identifier, TabConfig> recipeKeys) {
        List<Map.Entry<Identifier, GunSmithTableRecipe>> recipeList = source.recipes();
        List<Pair<Identifier, Identifier>> recipeIds = collectRecipeIds(recipeList, recipeKeys, true, true);
        boolean applyByHandFilter = true;
        boolean applyNamespaceFilter = true;

        if (recipeIds.isEmpty() && filterList != null && filterList.isByHandSelected()) {
            List<Pair<Identifier, Identifier>> withoutHandFilter = collectRecipeIds(recipeList, recipeKeys, false, true);
            if (!withoutHandFilter.isEmpty()) {
                filterList.setByHandSelected(false);
                applyByHandFilter = false;
                recipeIds = withoutHandFilter;
            }
        }

        if (recipeIds.isEmpty() && filterList != null && filterList.hasNamespaceOptions()) {
            List<Pair<Identifier, Identifier>> withoutNamespaceFilter = collectRecipeIds(recipeList, recipeKeys, applyByHandFilter, false);
            if (!withoutNamespaceFilter.isEmpty()) {
                filterList.selectAllNamespaces();
                applyNamespaceFilter = false;
                recipeIds = withoutNamespaceFilter;
            }
        }

        if (recipeIds.isEmpty() && filterList != null && applyByHandFilter && filterList.isByHandSelected()) {
            List<Pair<Identifier, Identifier>> withoutHandAndNamespaceFilter = collectRecipeIds(recipeList, recipeKeys, false, applyNamespaceFilter);
            if (!withoutHandAndNamespaceFilter.isEmpty()) {
                filterList.setByHandSelected(false);
                recipeIds = withoutHandAndNamespaceFilter;
            }
        }

        if (recipeIds.isEmpty() && filterList != null && (filterList.isByHandSelected() || filterList.hasNamespaceOptions())) {
            List<Pair<Identifier, Identifier>> withoutDestructiveFilters = collectRecipeIds(recipeList, recipeKeys, false, false);
            if (!withoutDestructiveFilters.isEmpty()) {
                filterList.setByHandSelected(false);
                filterList.selectAllNamespaces();
                recipeIds = withoutDestructiveFilters;
            }
        }

        List<Pair<Identifier, Identifier>> visibleRecipeIds = applyBlockRecipeFilter(recipeIds);
        Map<Identifier, GunSmithTableRecipe> recipeView = Maps.newLinkedHashMap();
        for (Pair<Identifier, Identifier> recipeId : visibleRecipeIds) {
            GunSmithTableRecipe recipe = source.get(recipeId.value());
            if (recipe != null) {
                recipeView.put(recipeId.value(), recipe);
            }
        }
        return new SourceEvaluation(visibleRecipeIds, recipeView);
    }

    private List<Pair<Identifier, Identifier>> applyBlockRecipeFilter(List<Pair<Identifier, Identifier>> recipeIds) {
        List<Pair<Identifier, Identifier>> filteredInput = recipeIds;
        Identifier blockId = getNormalizedBlockId();
        return getDisplayBlockIndex(blockId).map(blockIndex -> {
            if (DefaultAssets.DEFAULT_BLOCK_ID.equals(blockId) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
                return null;
            }
            RecipeFilter filter = blockIndex.getFilter();
            if (filter != null) {
                return filter.filter(filteredInput, Pair::value);
            }
            return null;
        }).orElse(filteredInput);
    }

    private SourceDiagnostics diagnoseRecipeSource(List<Map.Entry<Identifier, GunSmithTableRecipe>> recipeList,
                                                   Map<Identifier, TabConfig> recipeKeys) {
        int classified = 0;
        int emptyGroup = 0;
        int missingTab = 0;
        for (Map.Entry<Identifier, GunSmithTableRecipe> entry : recipeList) {
            Identifier group = entry.getValue().getResult().getGroup();
            if (group == null || TabConfig.TAB_EMPTY.equals(group)) {
                emptyGroup++;
            } else if (recipeKeys.containsKey(group)) {
                classified++;
            } else {
                missingTab++;
            }
        }
        return new SourceDiagnostics(recipeList.size(), classified, emptyGroup, missingTab);
    }

    private void logRecipeSourceDiagnostics(ClientGunSmithRecipeRepository.Source source,
                                            SourceDiagnostics diagnostics,
                                            int visibleCount,
                                            boolean selected) {
        Identifier blockId = getNormalizedBlockId();
        String key = blockId + "|" + source.kind().logName() + "|" + diagnostics.raw() + "|" + diagnostics.classified() + "|"
                + visibleCount + "|" + diagnostics.emptyGroup() + "|" + diagnostics.missingTab() + "|"
                + (filterList != null && filterList.isByHandSelected()) + "|"
                + (filterList != null && filterList.hasNamespaceOptions()) + "|" + selected;
        if (!LOGGED_RECIPE_SOURCE_DIAGNOSTICS.add(key)) {
            return;
        }
        if (diagnostics.raw() == 0) {
            GunMod.LOGGER.warn("Gun smith recipe source {} for block {} raw=0 classified=0 visible=0 emptyGroup=0 missingTab=0 byHandFilter={} namespaceFilter={}; empty source",
                    source.kind().logName(), blockId,
                    filterList != null && filterList.isByHandSelected(),
                    filterList != null && filterList.hasNamespaceOptions());
            return;
        }
        if (visibleCount == 0) {
            GunMod.LOGGER.warn("Gun smith recipe source {} for block {} raw={} classified={} visible={} emptyGroup={} missingTab={} byHandFilter={} namespaceFilter={}; skipping this source",
                    source.kind().logName(), blockId, diagnostics.raw(), diagnostics.classified(), visibleCount,
                    diagnostics.emptyGroup(), diagnostics.missingTab(),
                    filterList != null && filterList.isByHandSelected(),
                    filterList != null && filterList.hasNamespaceOptions());
            return;
        }
        GunMod.LOGGER.info("Gun smith recipe source {} {} for block {} raw={} classified={} visible={} emptyGroup={} missingTab={} byHandFilter={} namespaceFilter={}",
                source.kind().logName(), selected ? "selected" : "candidate",
                blockId, diagnostics.raw(), diagnostics.classified(), visibleCount,
                diagnostics.emptyGroup(), diagnostics.missingTab(),
                filterList != null && filterList.isByHandSelected(),
                filterList != null && filterList.hasNamespaceOptions());
    }

    @Nullable
    private Identifier getNormalizedBlockId() {
        return GunSmithTableBlockIds.normalize(menu.getBlockId());
    }

    private Optional<CommonBlockIndex> getDisplayBlockIndex(@Nullable Identifier blockId) {
        Identifier normalizedBlockId = GunSmithTableBlockIds.normalize(blockId);
        if (normalizedBlockId == null || DefaultAssets.EMPTY_BLOCK_ID.equals(normalizedBlockId)) {
            return Optional.empty();
        }
        Optional<CommonBlockIndex> commonBlockIndex = TimelessAPI.getCommonBlockIndex(normalizedBlockId);
        if (commonBlockIndex.isPresent()) {
            return commonBlockIndex;
        }
        CommonBlockIndex networkBlockIndex = CommonNetworkCache.INSTANCE.getBlockIndex(normalizedBlockId);
        if (networkBlockIndex != null) {
            if (WARNED_NETWORK_BLOCK_FALLBACK.add(normalizedBlockId)) {
                GunMod.LOGGER.warn("Using synchronized gun smith block index cache for {} because the common assets provider had no entry", normalizedBlockId);
            }
            return Optional.of(networkBlockIndex);
        }
        if (WARNED_MISSING_BLOCK_INDEX.add(normalizedBlockId)) {
            GunMod.LOGGER.warn("Gun smith table {} has no block index after normalization; recipe tabs cannot be built", normalizedBlockId);
        }
        return Optional.empty();
    }

    private List<Pair<Identifier, Identifier>> collectRecipeIds(List<Map.Entry<Identifier, GunSmithTableRecipe>> recipeList,
                                                                Map<Identifier, TabConfig> recipeKeys,
                                                                boolean applyByHandFilter,
                                                                boolean applyNamespaceFilter) {
        List<Pair<Identifier, Identifier>> recipeIds = Lists.newArrayList();
        Set<String> namespaces = applyNamespaceFilter && filterList != null && filterList.hasNamespaceOptions() ? filterList.namespaceList() : null;
        for (Map.Entry<Identifier, GunSmithTableRecipe> entry : recipeList) {
            Identifier id = entry.getKey();
            GunSmithTableRecipe recipe = entry.getValue();
            if (namespaces != null && !namespaces.contains(id.getNamespace())) {
                continue;
            }
            if (!isSuitableForMainHand(recipe, applyByHandFilter)) {
                continue;
            }
            if (!isNameMatch(recipe)) {
                continue;
            }
            if (!ClientGunSmithRecipeRepository.isDisplayable(recipe)) {
                continue;
            }

            Identifier groupName = recipe.getResult().getGroup();
            if (recipeKeys.containsKey(groupName)) {
                recipeIds.add(Pair.of(groupName, id));
            }
        }
        return recipeIds;
    }

    private boolean isNameMatch(GunSmithTableRecipe recipe) {
        if (filterList != null && StringUtils.isNotBlank(filterList.getSearchText())) {
            String searchText = filterList.getSearchText().toLowerCase();
            Component name = recipe.getResult().getResult().getHoverName();
            return name.getString().toLowerCase().contains(searchText);
        }
        return true;
    }

    private boolean isSuitableForMainHand(GunSmithTableRecipe recipe, boolean applyByHandFilter) {
        if (applyByHandFilter && filterList != null && filterList.isByHandSelected()) {
            ItemStack result = recipe.getResult().getResult();

            Minecraft minecraft = Minecraft.getInstance();
            ItemStack stack = minecraft.player != null ? minecraft.player.getMainHandItem() : ItemStack.EMPTY;
            if (stack.getItem() instanceof IGun igun) {
                if (result.getItem() instanceof IAmmo iAmmo) {
                    return iAmmo.isAmmoOfGun(stack, result);
                }
                if (result.getItem() instanceof IAttachment) {
                    return igun.allowAttachment(stack, result);
                }
                return false;
            }
            if (stack.getItem() instanceof IAttachment) {
                if (result.getItem() instanceof IGun iGun) {
                    return iGun.allowAttachment(result, stack);
                }
                return false;
            }
            if (stack.getItem() instanceof IAmmo iAmmo) {
                if (result.getItem() instanceof IGun) {
                    return iAmmo.isAmmoOfGun(result, stack);
                }
                return false;
            }
        }
        return true;
    }

    private boolean shouldFilterByMainHand() {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = minecraft.player != null ? minecraft.player.getMainHandItem() : ItemStack.EMPTY;
        Item item = stack.getItem();
        return item instanceof IGun || item instanceof IAttachment || item instanceof IAmmo;
    }

    private void updateSelectedRecipeAfterFiltering() {
        if (selectedRecipeList == null || selectedRecipeList.isEmpty()) {
            this.selectedRecipe = null;
            this.selectedRecipeId = null;
            this.playerIngredientCount = null;
            return;
        }
        boolean selectedRecipeExists = this.selectedRecipeId != null && selectedRecipeList.contains(this.selectedRecipeId);
        if (!selectedRecipeExists) {
            this.selectRecipe(selectedRecipeList.get(0));
        } else {
            this.selectRecipe(this.selectedRecipeId);
        }
        this.getPlayerIngredientCount(this.selectedRecipe);
    }

    public void setIndexPage(int indexPage) {
        this.indexPage = indexPage;
    }

    @Nullable
    private GunSmithTableRecipe getSelectedRecipe(@Nullable Identifier recipeId) {
        if (recipeId == null) {
            return null;
        }
        return recipeView.get(recipeId);
    }

    private record SourceEvaluation(List<Pair<Identifier, Identifier>> recipeIds,
                                    Map<Identifier, GunSmithTableRecipe> recipeView) {
        private static SourceEvaluation empty() {
            return new SourceEvaluation(List.of(), Map.of());
        }
    }

    private record SourceCandidate(ClientGunSmithRecipeRepository.Source source,
                                   SourceDiagnostics diagnostics,
                                   SourceEvaluation evaluation) {
    }

    private record SourceDiagnostics(int raw, int classified, int emptyGroup, int missingTab) {
    }

    private void selectRecipe(@Nullable Identifier recipeId) {
        this.selectedRecipeId = recipeId;
        this.selectedRecipe = this.getSelectedRecipe(recipeId);
    }

    private void getPlayerIngredientCount(GunSmithTableRecipe recipe) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || recipe == null) {
            return;
        }
        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        int size = ingredients.size();
        this.playerIngredientCount = new Int2IntArrayMap(size);
        for (int i = 0; i < size; i++) {
            GunSmithTableIngredient ingredient = ingredients.get(i);
            Inventory inventory = player.getInventory();
            int count = 0;
            for (ItemStack stack : inventory.getNonEquipmentItems()) {
                if (!stack.isEmpty() && ingredient.getIngredient().test(stack)) {
                    count = count + stack.getCount();
                }
            }
            playerIngredientCount.put(i, count);
        }
    }

    public void updateIngredientCount() {
        if (this.selectedRecipe != null) {
            this.getPlayerIngredientCount(selectedRecipe);
        }
        this.init();
    }

    public void refreshRecipesFromClientData() {
        this.refreshRecipesFromClientData(true);
    }

    private void refreshRecipesFromClientData(boolean resetRetry) {
        if (resetRetry) {
            this.emptyRecipeRefreshTicks = 0;
            this.emptyRecipeRefreshAttempts = 0;
        }
        this.filterList = null;
        this.classifyRecipes();
        this.updateSelectedRecipeAfterFiltering();
        this.init();
    }

    private void retryEmptyRecipeRefresh() {
        if (!this.recipeView.isEmpty()) {
            return;
        }
        if (this.emptyRecipeRefreshAttempts >= EMPTY_RECIPE_REFRESH_ATTEMPTS) {
            return;
        }
        if (++this.emptyRecipeRefreshTicks < EMPTY_RECIPE_REFRESH_INTERVAL) {
            return;
        }
        this.emptyRecipeRefreshTicks = 0;
        this.emptyRecipeRefreshAttempts++;
        this.refreshRecipesFromClientData(false);
    }

    @Override
    public void init() {
        super.init();
        if (this.filterList == null) {
            this.filterList = new GunPackList(this.minecraft, 134, this.imageHeight, topPos, topPos+imageHeight+1, 15, recipes, this);
        }
        if (!this.autoByHandFilterApplied && RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.get()) {
            this.filterList.setByHandSelected(this.shouldFilterByMainHand());
            this.autoByHandFilterApplied = true;
        }
        this.filterList.updateSizeAndPosition(134, this.imageHeight + 1, leftPos, topPos);

        this.classifyRecipes();
        this.updateSelectedRecipeAfterFiltering();
        this.clearWidgets();

        this.addTypePageButtons();
        this.addTypeButtons();
        this.addIndexPageButtons();
        this.addIndexButtons();
        this.addRenderableWidget(new FlatColorButton(leftPos - 10, topPos, 9, 9, Component.literal("F"), b -> {
            this.filterEnabled = !this.filterEnabled;
            this.init();
        }).setTooltips("gui.tacz.gun_smith_table.filter"));
        if(this.filterEnabled) {
            this.addRenderableWidget(this.filterList);
        } else {
            this.addScaleButtons();
            this.addUrlButton();
        }
        this.addCraftButton();
    }

    private void addCraftButton() {
        this.addRenderableWidget(new TextureImageButton(leftPos + 289, topPos + 162, 48, 18, 138, 164, 18, TEXTURE, b -> {
            if (this.selectedRecipe != null && this.selectedRecipeId != null && playerIngredientCount != null) {
                // 检查是否能合成，不能就不发包
                List<GunSmithTableIngredient> inputs = selectedRecipe.getInputs();
                int size = inputs.size();
                for (int i = 0; i < size; i++) {
                    if (i >= playerIngredientCount.size()) {
                        return;
                    }
                    int hasCount = playerIngredientCount.get(i);
                    int needCount = inputs.get(i).getCount();
                    boolean isCreative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
                    // 拥有数量小于需求数量，不发包
                    if (hasCount < needCount && !isCreative) {
                        return;
                    }
                }
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageCraft(this.selectedRecipeId, this.menu.containerId));
            }
        }));
    }

    private void addUrlButton() {
        this.addRenderableWidget(new TextureImageButton(leftPos + 112, topPos + 164, 18, 18, 149, 211, 18, TEXTURE, b -> {
            if (this.selectedRecipe != null) {
                ItemStack output = selectedRecipe.getOutput();
                Item item = output.getItem();
                Identifier id;
                if (item instanceof IGun iGun) {
                    id = iGun.getGunId(output);
                } else if (item instanceof IAttachment iAttachment) {
                    id = iAttachment.getAttachmentId(output);
                } else if (item instanceof IAmmo iAmmo) {
                    id = iAmmo.getAmmoId(output);
                } else {
                    return;
                }

                PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
                if (packInfo == null) {
                    return;
                }
                String url = packInfo.getUrl();
                if (StringUtils.isNotBlank(url) && minecraft != null) {
                    MinecraftGuiCompat.setScreen(new ConfirmLinkScreen(yes -> {
                        if (yes) {
                            Util.getPlatform().openUri(url);
                        }
                        MinecraftGuiCompat.setScreen(this);
                    }, url, false));
                }
            }
        }));
    }

    private void addIndexButtons() {
        if (selectedRecipeList == null || selectedRecipeList.isEmpty()) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            int finalIndex = i + indexPage * 6;
            if (finalIndex >= selectedRecipeList.size()) {
                break;
            }
            int yOffset = topPos + 66 + 17 * i;
            Identifier recipeId = selectedRecipeList.get(finalIndex);
            GunSmithTableRecipe recipe = getSelectedRecipe(recipeId);
            if (recipe == null) {
                continue;
            }
            ResultButton button = addRenderableWidget(new ResultButton(leftPos + 144, yOffset, recipe.getOutput(), b -> {
                this.selectRecipe(recipeId);
                this.getPlayerIngredientCount(this.selectedRecipe);
                this.init();
            }));
            if (recipeId.equals(this.selectedRecipeId)) {
                button.setSelected(true);
            }
        }
    }

    private void addTypeButtons() {
        var list = Arrays.asList(recipeKeys.values().toArray(new TabConfig[0]));
        for (int i = 0; i < 7; i++) {
            int typeIndex = typePage * 7 + i;
            if (typeIndex >= recipes.size()) {
                return;
            }
            TabConfig tabConfig = list.get(typeIndex);
            Identifier type = tabConfig.id();
            int xOffset = leftPos + 157 + 24 * i;

            ItemStack icon = tabConfig.icon();

            TypeButton typeButton = new TypeButton(xOffset, topPos + 2, icon, b -> {
                this.selectedType = type;
                this.selectedRecipeList = recipes.get(type);
                this.indexPage = 0;
                this.selectRecipe(this.selectedRecipeList.isEmpty() ? null : this.selectedRecipeList.get(0));
                this.getPlayerIngredientCount(this.selectedRecipe);
                this.init();
            });
            typeButton.setTooltip(Tooltip.create(tabConfig.getName(), tabConfig.getName()));
            if (this.selectedType.equals(type)) {
                typeButton.setSelected(true);
            }
            this.addRenderableWidget(typeButton);
        }
    }

    private void addIndexPageButtons() {
        this.addRenderableWidget(new TextureImageButton(leftPos + 143, topPos + 56, 96, 6, 40, 166, 6, TEXTURE, b -> {
            if (this.indexPage > 0) {
                this.indexPage--;
                this.init();
            }
        }));
        this.addRenderableWidget(new TextureImageButton(leftPos + 143, topPos + 171, 96, 6, 40, 186, 6, TEXTURE, b -> {
            if (selectedRecipeList != null && !selectedRecipeList.isEmpty()) {
                int maxIndexPage = (selectedRecipeList.size() - 1) / 6;
                if (this.indexPage < maxIndexPage) {
                    this.indexPage++;
                    this.init();
                }
            }
        }));
    }

    private void addTypePageButtons() {
        this.addRenderableWidget(new TextureImageButton(leftPos + 136, topPos + 4, 18, 20, 0, 162, 20, TEXTURE, b -> {
            if (this.typePage > 0) {
                this.typePage--;
                this.init();
            }
        }));
        this.addRenderableWidget(new TextureImageButton(leftPos + 327, topPos + 4, 18, 20, 20, 162, 20, TEXTURE, b -> {
            int maxIndexPage = (recipes.size() - 1) / 7;
            if (this.typePage < maxIndexPage) {
                this.typePage++;
                this.init();
            }
        }));
    }

    private void addScaleButtons() {
        this.addRenderableWidget(new TextureImageButton(leftPos + 5, topPos + 5, 10, 10, 188, 173, 10, TEXTURE, b -> {
            this.scale = Math.min(this.scale + 20, 200);
        }));
        this.addRenderableWidget(new TextureImageButton(leftPos + 17, topPos + 5, 10, 10, 200, 173, 10, TEXTURE, b -> {
            this.scale = Math.max(this.scale - 20, 10);
        }));
        this.addRenderableWidget(new TextureImageButton(leftPos + 29, topPos + 5, 10, 10, 212, 173, 10, TEXTURE, b -> {
            this.scale = 70;
        }));
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double scrollX, double scrollY) {
        if (pMouseX > leftPos + 143 && pMouseX < leftPos + 143 + 94 && pMouseY > topPos + 66 && pMouseY < topPos + 66 + 85) {
            if (scrollY > 0) {
                this.indexPage = Math.max(0, this.indexPage - 1);
            } else if (selectedRecipeList != null && !selectedRecipeList.isEmpty()) {
                int maxIndexPage = (selectedRecipeList.size() - 1) / 6;
                this.indexPage = Math.min(maxIndexPage, this.indexPage + 1);
            }
            this.init();
            return true;
        }
        return super.mouseScrolled(pMouseX, pMouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.retryEmptyRecipeRefresh();
        drawModCenteredString(graphics, font, Component.translatable("gui.tacz.gun_smith_table.preview"), leftPos + 108, topPos + 5, 0x555555);
        if (selectedType != null) {
            var config = recipeKeys.get(selectedType);
            if (config != null) {
                graphics.text(font, config.getName(), leftPos + 150, topPos + 32, 0x555555, false);
            }
        }
        graphics.text(font, Component.translatable("gui.tacz.gun_smith_table.ingredient"), leftPos + 254, topPos + 50, 0x555555, false);
        drawModCenteredString(graphics, font, Component.translatable("gui.tacz.gun_smith_table.craft"), leftPos + 312, topPos + 167, 0xFFFFFF);
        if (!this.filterEnabled && this.selectedRecipe != null) {
            this.extractLeftModel(graphics, this.selectedRecipe);
            this.renderPackInfo(graphics, this.selectedRecipe);
            graphics.text(font, Component.translatable("gui.tacz.gun_smith_table.count", this.selectedRecipe.getResult().getResult().getCount()), leftPos + 254, topPos + 140, 0x555555, false);
        }
        if (selectedRecipeList != null && !selectedRecipeList.isEmpty()) {
            renderIngredient(graphics);
        }

        this.renderables.stream().filter(w -> w instanceof ResultButton)
                .forEach(w -> ((ResultButton) w).renderTooltips(stack -> graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)));
    }

    private void renderPackInfo(GuiGraphicsExtractor gui, GunSmithTableRecipe recipe) {
        ItemStack output = recipe.getOutput();
        Item item = output.getItem();
        Identifier id;
        if (item instanceof IGun iGun) {
            id = iGun.getGunId(output);
        } else if (item instanceof IAttachment iAttachment) {
            id = iAttachment.getAttachmentId(output);
        } else if (item instanceof IAmmo iAmmo) {
            id = iAmmo.getAmmoId(output);
        } else {
            return;
        }

        PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
        if (packInfo != null) {
            Component nameText = Component.translatable(packInfo.getName());
            submitLocalScaledText(gui, nameText, leftPos + 6, topPos + 122, 0.75f, 0x555555, false);

            int offsetX = leftPos + 6;
            int offsetY = topPos + 130;
            int nameWidth = (int) (font.width(nameText) * 0.75f);
            Component ver = Component.literal("v" + packInfo.getVersion()).withStyle(ChatFormatting.UNDERLINE);
            submitLocalScaledText(gui, ver, offsetX + nameWidth + 5, topPos + 123, 0.5f, 0x555555, false);

            String descKey = packInfo.getDescription();
            if (StringUtils.isNoneBlank(descKey)) {
                Component desc = Component.translatable(descKey);
                List<FormattedCharSequence> split = font.split(desc, 245);
                for (FormattedCharSequence charSequence : split) {
                    submitLocalScaledText(gui, charSequence, offsetX, offsetY, 0.5f, 0x555555, false);
                    offsetY += 5;
                }
                offsetY += 3;
            }

            submitLocalScaledText(gui, Component.translatable("gui.tacz.gun_smith_table.license")
                            .append(Component.literal(packInfo.getLicense()).withStyle(ChatFormatting.DARK_GRAY)),
                    offsetX, offsetY, 0.5f, 0x555555, false);
            offsetY += 12;

            List<String> authors = packInfo.getAuthors();
            if (!authors.isEmpty()) {
                submitLocalScaledText(gui, Component.translatable("gui.tacz.gun_smith_table.authors")
                                .append(Component.literal(StringUtils.join(authors, ", ")).withStyle(ChatFormatting.DARK_GRAY)),
                        offsetX, offsetY, 0.5f, 0x555555, false);
                offsetY += 12;
            }

            submitLocalScaledText(gui, Component.translatable("gui.tacz.gun_smith_table.date")
                            .append(Component.literal(packInfo.getDate()).withStyle(ChatFormatting.DARK_GRAY)),
                    offsetX, offsetY, 0.5f, 0x555555, false);
        } else {
            String recipeText = this.selectedRecipeId == null ? "unknown" : this.selectedRecipeId.toString();
            gui.text(font, Component.translatable("gui.tacz.gun_smith_table.error").withStyle(ChatFormatting.DARK_RED), leftPos + 6, topPos + 122, 0xAF0000, false);
            gui.text(font, Component.translatable("gui.tacz.gun_smith_table.error.id", recipeText).withStyle(ChatFormatting.DARK_RED), leftPos + 6, topPos + 134, 0xFFFFFF, false);
            PackInfo errorPackInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
            if (errorPackInfo != null) {
                gui.text(font, Component.translatable(errorPackInfo.getName()).withStyle(ChatFormatting.DARK_RED), leftPos + 6, topPos + 146, 0xAF0000, false);
            }
        }
    }

    private void renderIngredient(GuiGraphicsExtractor gui) {
        if (this.selectedRecipe == null) {
            return;
        }
        List<GunSmithTableIngredient> inputs = this.selectedRecipe.getInputs();
        warnIfIngredientListTruncated(inputs);
        for (int i = 0; i < INGREDIENT_ROWS; i++) {
            for (int j = 0; j < INGREDIENT_COLUMNS; j++) {
                int index = i * INGREDIENT_COLUMNS + j;
                if (index >= inputs.size()) {
                    return;
                }
                int offsetX = leftPos + 254 + 45 * j;
                int offsetY = topPos + 62 + 17 * i;

                GunSmithTableIngredient smithTableIngredient = inputs.get(index);
                Ingredient ingredient = smithTableIngredient.getIngredient();

                ItemStack[] items = ingredient.items().map(ItemStack::new).toArray(ItemStack[]::new);
                if (items.length == 0) {
                    warnEmptyIngredient(index);
                    continue;
                }
                int itemIndex = ((int) (System.currentTimeMillis() / 1_000)) % items.length;
                ItemStack item = items[itemIndex];

                submitIngredientItem(gui, item, offsetX, offsetY);
                int count = smithTableIngredient.getCount();
                if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative()){
                    submitLocalScaledText(gui, String.format("%d/∞", count), offsetX + 17, offsetY + 10, 0.5f, 0xFFFFFF, false);
                } else {
                    int hasCount = 0;
                    if (playerIngredientCount != null && index < playerIngredientCount.size()) {
                        hasCount = playerIngredientCount.get(index);
                    }
                    int color = count <= hasCount ? 0xFFFFFF : 0xFF0000;
                    submitLocalScaledText(gui, String.format("%d/%d", count, hasCount), offsetX + 17, offsetY + 10, 0.5f, color, false);
                }
            }
        }
    }

    private void submitIngredientItem(GuiGraphicsExtractor gui, ItemStack item, int x, int y) {
        if (item.isEmpty()) {
            warnEmptyIngredient(-1);
            return;
        }
        ItemStack icon = item.copyWithCount(1);
        gui.enableScissor(x, y, x + INGREDIENT_SLOT_SIZE, y + INGREDIENT_SLOT_SIZE);
        try {
            gui.item(icon, x, y);
        } finally {
            gui.disableScissor();
        }
    }

    private void warnIfIngredientListTruncated(List<GunSmithTableIngredient> inputs) {
        if (inputs.size() <= MAX_RENDERED_INGREDIENTS || this.selectedRecipeId == null) {
            return;
        }
        if (WARNED_TRUNCATED_INGREDIENTS.add(this.selectedRecipeId)) {
            GunMod.LOGGER.warn("Gun smith table recipe {} has {} inputs, but the retained UI can render only {} material slots",
                    this.selectedRecipeId, inputs.size(), MAX_RENDERED_INGREDIENTS);
        }
    }

    private void warnEmptyIngredient(int index) {
        if (this.selectedRecipeId == null) {
            return;
        }
        if (WARNED_EMPTY_INGREDIENTS.add(this.selectedRecipeId)) {
            GunMod.LOGGER.warn("Gun smith table recipe {} has an empty material ingredient at slot {}",
                    this.selectedRecipeId, index);
        }
    }

    private void submitLocalScaledText(GuiGraphicsExtractor gui, Component text, int x, int y, float scale, int color, boolean shadow) {
        Matrix3x2fStack poseStack = gui.pose();
        poseStack.pushMatrix();
        poseStack.translate(x, y);
        poseStack.scale(scale, scale);
        gui.text(font, text, 0, 0, color, shadow);
        poseStack.popMatrix();
    }

    private void submitLocalScaledText(GuiGraphicsExtractor gui, FormattedCharSequence text, int x, int y, float scale, int color, boolean shadow) {
        Matrix3x2fStack poseStack = gui.pose();
        poseStack.pushMatrix();
        poseStack.translate(x, y);
        poseStack.scale(scale, scale);
        gui.text(font, text, 0, 0, color, shadow);
        poseStack.popMatrix();
    }

    private void submitLocalScaledText(GuiGraphicsExtractor gui, String text, int x, int y, float scale, int color, boolean shadow) {
        Matrix3x2fStack poseStack = gui.pose();
        poseStack.pushMatrix();
        poseStack.translate(x, y);
        poseStack.scale(scale, scale);
        gui.text(font, text, 0, 0, color, shadow);
        poseStack.popMatrix();
    }

    private void extractLeftModel(GuiGraphicsExtractor graphics, GunSmithTableRecipe recipe) {
        // 先标记一下，渲染高模
        RenderDistance.markGuiRenderTimestamp();

        int xPos = leftPos + 60;
        int yPos = topPos + 50;
        int startX = leftPos + 3;
        int startY = topPos + 16;
        int width = 128;
        int height = 99;
        ItemStack output = recipe.getOutput();
        if (output.isEmpty()) {
            return;
        }

        float itemScale = Math.max(1.0f, this.scale / 16.0f);
        graphics.enableScissor(startX, startY, startX + width, startY + height);
        Matrix3x2fStack poseStack = graphics.pose();
        poseStack.pushMatrix();
        poseStack.translate(xPos, yPos);
        poseStack.scale(itemScale, itemScale);
        graphics.item(output, -8, -8);
        poseStack.popMatrix();
        graphics.disableScissor();
    }

    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY) {
    }

    @Override
    public void extractContents(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        gui.blit(RenderPipelines.GUI_TEXTURED, SIDE, leftPos, topPos, 0, 0, 134, 187, 256, 256);
        gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + 136, topPos + 27, 0, 0, 208, 160, 256, 256);
        super.extractContents(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
