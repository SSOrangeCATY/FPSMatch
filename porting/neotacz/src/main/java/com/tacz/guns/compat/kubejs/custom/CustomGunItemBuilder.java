package com.tacz.guns.compat.kubejs.custom;

import com.tacz.guns.api.item.gun.GunItemManager;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

public class CustomGunItemBuilder extends ItemBuilder {
    public String typeName;

    public CustomGunItemBuilder(Identifier i) {
        super(i);
        this.typeName = "kubejs_default";
    }

    public void setTypeName(String name) {
        this.typeName = name;
    }

    @Override
    public Item createObject() {
        GunItemManager.registerGunItem(typeName, DeferredHolder.create(ResourceKey.create(Registries.ITEM, this.id)));
        return new KubeJSCustomGunItem(createItemProperties());
    }
}
