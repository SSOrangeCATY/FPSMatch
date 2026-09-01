package com.ptcrys.fpsmatch.common.client.screen.shop.ldlib2;

import java.util.List;

/** Stable IDs for the LDLib2 shop editor and child slot editor. */
public final class ShopEditorWidgetCatalog {
    public static final String ROOT = "fpsmatch.shop_editor.root";
    public static final String HEADER = "fpsmatch.shop_editor.header";
    public static final String SUBTITLE = "fpsmatch.shop_editor.subtitle";
    public static final String CATEGORIES = "fpsmatch.shop_editor.categories";
    public static final String GROUP = "fpsmatch.shop_editor.group";
    public static final String SLOT_EDITOR = "fpsmatch.shop_editor.slot_editor";
    public static final String CATEGORY_TABS = "fpsmatch.shop_editor.category_tabs";
    public static final String SLOT_LIST = "fpsmatch.shop_editor.slot_list";
    public static final String PROPERTIES = "fpsmatch.shop_editor.properties";
    public static final String ACTIONS = "fpsmatch.shop_editor.actions";
    public static final String ITEM = "fpsmatch.shop_editor.item";
    public static final String AMMO = "fpsmatch.shop_editor.ammo";
    public static final String PRICE = "fpsmatch.shop_editor.price";
    public static final String SAVE = "fpsmatch.shop_editor.save";
    public static final String EDIT_SELECTED = "fpsmatch.shop_editor.edit_selected";
    public static final String STATUS = "fpsmatch.shop_editor.status";
    public static final String CLOSE = "fpsmatch.shop_editor.close";

    private ShopEditorWidgetCatalog() {
    }

    public static List<String> ids() {
        return List.of(ROOT, HEADER, CATEGORY_TABS, SLOT_LIST, PROPERTIES, ACTIONS, ITEM, AMMO,
                PRICE, GROUP, SAVE, EDIT_SELECTED, STATUS, CLOSE);
    }
}
