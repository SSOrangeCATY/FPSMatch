package com.tacz.guns.client.gui.compat;

import com.tacz.guns.init.CompatRegistry;
import com.tacz.guns.util.MinecraftGuiCompat;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class ClothConfigScreen extends Screen {
    public static final String CLOTH_CONFIG_URL = "https://www.curseforge.com/minecraft/mc-mods/cloth-config";
    private final Screen lastScreen;
    private MultiLineLabel message = MultiLineLabel.EMPTY;

    protected ClothConfigScreen(Screen lastScreen) {
        super(Component.literal("Cloth Config API"));
        this.lastScreen = lastScreen;
    }

    public static void registerNoClothConfigPage() {
        if (!ModList.get().isLoaded(CompatRegistry.CLOTH_CONFIG)) {
            Supplier<IConfigScreenFactory> factory = () -> (container, parent) -> new ClothConfigScreen(parent);
            ModLoadingContext.get().getActiveContainer().registerExtensionPoint(IConfigScreenFactory.class, factory);
        }
    }

    @Override
    protected void init() {
        int posX = (this.width - 200) / 2;
        int posY = this.height / 2;
        this.message = MultiLineLabel.create(this.font, Component.translatable("gui.tacz.cloth_config_warning.tips"), 300);
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.tacz.cloth_config_warning.download"), b -> openUrl(CLOTH_CONFIG_URL))
                        .bounds(posX, posY - 15, 200, 20).build()
        );
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK, b -> MinecraftGuiCompat.setScreen(this.lastScreen))
                        .bounds(posX, posY + 50, 200, 20).build()
        );
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        this.message.visitLines(TextAlignment.CENTER, this.width / 2, 80, 9, gui.textRenderer());
    }

    private void openUrl(String url) {
        if (StringUtils.isNotBlank(url) && minecraft != null) {
            MinecraftGuiCompat.setScreen(new ConfirmLinkScreen(yes -> {
                if (yes) {
                    Util.getPlatform().openUri(url);
                }
                MinecraftGuiCompat.setScreen(this);
            }, url, true));
        }
    }
}
