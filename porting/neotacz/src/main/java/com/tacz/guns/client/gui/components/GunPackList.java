package com.tacz.guns.client.gui.components;

import com.google.common.collect.ImmutableList;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.*;

public class GunPackList extends ContainerObjectSelectionList<GunPackList.Entry> {
    private final GunSmithTableScreen parent;
    private final List<Checkbox> gunPackList = new ArrayList<>();
    private final Set<String> selectedNamespaces = new HashSet<>();
    private final Checkbox byHandCheckbox;
    private final Checkbox allCheckbox;
    private final EditBox byName;

    public GunPackList(Minecraft pMinecraft, int pWidth, int pHeight, int pY0, int pY1, int pItemHeight,
                       Map<Identifier, List<Identifier>> recipes, GunSmithTableScreen parent) {
        super(pMinecraft, pWidth, pY1 - pY0, pY0, pItemHeight);
        this.parent = parent;
        Set<String> namespaces = new HashSet<>();
        for (List<Identifier> entry : recipes.values()) {
            entry.forEach((resourceLocation) -> namespaces.add(resourceLocation.getNamespace()));
        }

        this.byName = new EditBox(pMinecraft.font, 3, 0, 94, 10, Component.empty());
        this.byName.setHint(Component.translatable("gui.tacz.gun_smith_table.filter.search"));
        this.byName.setResponder((pText) -> {
            parent.init();
            parent.setIndexPage(0);
        });
        this.addEntry(new GunPackList.Entry(byName));

        this.byHandCheckbox = new Checkbox(0, 0, 10, 10, Component.translatable("gui.tacz.gun_smith_table.filter.handgun"), false) {
            @Override
            public void onPress(InputWithModifiers input) {
                super.onPress(input);
                parent.init();
                parent.setIndexPage(0);
            }
        };
        this.addEntry(new GunPackList.Entry(byHandCheckbox));

        Checkbox checkbox1 = new Checkbox(0, 0, 10, 10, Component.translatable("gui.tacz.gun_smith_table.filter.all"), true) {
            @Override
            public void onPress(InputWithModifiers input) {
                super.onPress(input);
                gunPackList.forEach((checkbox) -> checkbox.selected = this.selected);
                updateSelectedNamespaces();
            }
        };
        this.allCheckbox = checkbox1;
        this.addEntry(new GunPackList.Entry(checkbox1));

        for (String namespace : namespaces) {
            PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(namespace);
            Component name = packInfo == null ? Component.literal(namespace) : Component.translatable(packInfo.getName());

            Checkbox checkbox = new Checkbox(0, 0, 10, 10, name, namespace, true) {
                @Override
                public void onPress(InputWithModifiers input) {
                    super.onPress(input);
                    checkbox1.selected = gunPackList.stream().allMatch(Checkbox::selected);
                    updateSelectedNamespaces();
                }
            };
            gunPackList.add(checkbox);
            selectedNamespaces.add(namespace);
            this.addEntry(new GunPackList.Entry(checkbox));
        }
    }

    public String getSearchText() {
        return byName.getValue();
    }

    public boolean isByHandSelected() {
        return byHandCheckbox.selected;
    }

    public void setByHandSelected(boolean selected) {
        byHandCheckbox.selected = selected;
    }

    public Set<String> namespaceList() {
        return selectedNamespaces;
    }

    public void selectAllNamespaces() {
        allCheckbox.selected = true;
        selectedNamespaces.clear();
        for (Checkbox checkbox : gunPackList) {
            checkbox.selected = true;
            selectedNamespaces.add(checkbox.getId());
        }
    }

    public boolean hasNamespaceOptions() {
        return !gunPackList.isEmpty();
    }

    public void updateSelectedNamespaces() {
        selectedNamespaces.clear();
        gunPackList.forEach((checkbox) -> {
            if (checkbox.selected) {
                selectedNamespaces.add(checkbox.getId());
            }
        });
        parent.init();
        parent.setIndexPage(0);
    }

    @Override
    protected void extractListBackground(GuiGraphicsExtractor graphics) {
        graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0x80000000);
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
    }

    @Override
    protected int scrollBarX() {
        return this.getRight() - 2;
    }

    @Override
    protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int maxScroll = this.maxScrollAmount();
        if (maxScroll > 0) {
            int x0 = this.scrollBarX();
            int x1 = x0 + 6;
            int scrollerHeight = this.scrollerHeight();
            int y0 = this.scrollBarY();
            graphics.fill(x0, y0, x1, y0 + scrollerHeight, -8355712);
            graphics.fill(x0, y0, x1 - 1, y0 + scrollerHeight - 1, -4144960);
        }
    }

    public int getRowLeft() {
        return this.getX() + 4;
    }

    public int getRowWidth() {
        return this.getWidth();
    }

    public static class Entry extends ContainerObjectSelectionList.Entry<GunPackList.Entry> {
        private final AbstractWidget widget;

        public Entry(AbstractWidget widget) {
            this.widget = widget;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
             return ImmutableList.of(widget);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            this.widget.setX(this.getContentX());
            this.widget.setY(this.getContentY());
            this.widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(widget);
        }
    }

    public static class Checkbox extends AbstractButton {
        protected boolean selected;
        protected final boolean showLabel;
        private String id;

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, String id, boolean pSelected) {
            this(pX, pY, pWidth, pHeight, pMessage, pSelected, true);
            this.id = id;
        }

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, boolean pSelected) {
            this(pX, pY, pWidth, pHeight, pMessage, pSelected, true);
        }

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, boolean pSelected, boolean pShowLabel) {
            super(pX, pY, pWidth, pHeight, pMessage);
            this.selected = pSelected;
            this.showLabel = pShowLabel;
        }

        public String getId() {
            return id;
        }

        private void toggle() {
            this.selected = !this.selected;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            this.toggle();
        }

        public boolean selected() {
            return this.selected;
        }

        public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {
            pNarrationElementOutput.add(NarratedElementType.TITLE, this.createNarrationMessage());
            if (this.active) {
                if (this.isFocused()) {
                    pNarrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.focused"));
                } else {
                    pNarrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.hovered"));
                }
            }

        }

        @Override
        protected void extractContents(GuiGraphicsExtractor pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            Font font = minecraft.font;
            int alpha = Mth.ceil(this.alpha * 255.0F) << 24;
            int x = this.getX();
            int y = this.getY();
            int borderColor = alpha | (this.isFocused() ? 0xFFFFFF : 0xC0C0C0);
            int fillColor = alpha | (this.selected ? 0x223344 : 0x101010);
            int checkColor = alpha | 0x66FFCC;
            pGuiGraphics.fill(x, y, x + 10, y + 10, borderColor);
            pGuiGraphics.fill(x + 1, y + 1, x + 9, y + 9, fillColor);
            if (this.selected) {
                pGuiGraphics.fill(x + 2, y + 5, x + 4, y + 7, checkColor);
                pGuiGraphics.fill(x + 4, y + 7, x + 5, y + 8, checkColor);
                pGuiGraphics.fill(x + 5, y + 4, x + 7, y + 6, checkColor);
                pGuiGraphics.fill(x + 7, y + 2, x + 8, y + 4, checkColor);
            }
            if (this.showLabel) {
                pGuiGraphics.text(font, this.getMessage(), this.getX() + 24, this.getY() + (this.height - 8) / 2, 14737632 | alpha);
            }

        }
    }
}
