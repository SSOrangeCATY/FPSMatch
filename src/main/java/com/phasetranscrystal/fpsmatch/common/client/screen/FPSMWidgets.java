package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import net.minecraft.network.chat.Component;

/**
 * LDLib UI组件构建工具方法
 */
public final class FPSMWidgets {

    private FPSMWidgets() {}

    /** 创建带文字的标准按钮 */
    public static ButtonWidget button(int x, int y, int w, int h, Component text, Runnable action) {
        return new ButtonWidget(x, y, w, h,
                ResourceBorderTexture.BUTTON_COMMON,
                cd -> action.run())
                .setButtonTexture(
                        ResourceBorderTexture.BUTTON_COMMON,
                        new TextTexture(text.getString(), -1).setWidth(w).setType(TextTexture.TextType.ROLL))
                .setHoverBorderTexture(1, -1);
    }

    /** 创建带文字的标准按钮（无背景按钮） */
    public static ButtonWidget textButton(int x, int y, int w, int h, Component text, Runnable action) {
        return new ButtonWidget(x, y, w, h, cd -> action.run())
                .setButtonTexture(new TextTexture(text.getString(), -1).setWidth(w).setType(TextTexture.TextType.ROLL))
                .setHoverTexture(new TextTexture(text.getString(), 0xFFFFFFAA).setWidth(w).setType(TextTexture.TextType.ROLL));
    }

    /** 创建面板容器 */
    public static WidgetGroup panel(int x, int y, int w, int h, int bgColor) {
        WidgetGroup group = new WidgetGroup(x, y, w, h);
        group.setBackground(new com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture(bgColor));
        return group;
    }

    /** 创建自适应大小的面板容器 */
    public static WidgetGroup autoPanel(Position pos) {
        return new WidgetGroup(pos);
    }
}