package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.item.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.core.registries.Registries;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GunMod.MOD_ID);
    public static final Identifier WORKBENCH_A_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "ammo_workbench");
    public static final Identifier WORKBENCH_B_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_smith_table");
    public static final Identifier WORKBENCH_C_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "attachment_workbench");

    public static DeferredItem<ModernKineticGunItem> MODERN_KINETIC_GUN = ITEMS.registerItem("modern_kinetic_gun", ModernKineticGunItem::new);

//    public static DeferredHolder<Item, ThrowableItem> M67 = ITEMS.register("m67", ThrowableItem::new);

    public static DeferredItem<AmmoItem> AMMO = ITEMS.registerItem("ammo", AmmoItem::new);
    public static DeferredItem<AttachmentItem> ATTACHMENT = ITEMS.registerItem("attachment", AttachmentItem::new);

    public static DeferredItem<GunSmithTableItem> GUN_SMITH_TABLE = ITEMS.registerItem("gun_smith_table", properties -> new DefaultTableItem(ModBlocks.GUN_SMITH_TABLE.get(), properties));
    public static DeferredItem<GunSmithTableItem> WORKBENCH_111 = ITEMS.registerItem("workbench_a", properties -> new GunSmithTableItem(ModBlocks.WORKBENCH_111.get(), properties, WORKBENCH_A_ID));
    public static DeferredItem<GunSmithTableItem> WORKBENCH_211 = ITEMS.registerItem("workbench_b", properties -> new GunSmithTableItem(ModBlocks.WORKBENCH_211.get(), properties, WORKBENCH_B_ID));
    public static DeferredItem<GunSmithTableItem> WORKBENCH_121 = ITEMS.registerItem("workbench_c", properties -> new GunSmithTableItem(ModBlocks.WORKBENCH_121.get(), properties, WORKBENCH_C_ID));


    public static DeferredItem<BlockItem> TARGET = ITEMS.registerSimpleBlockItem(ModBlocks.TARGET);
    public static DeferredItem<BlockItem> STATUE = ITEMS.registerSimpleBlockItem(ModBlocks.STATUE);
    public static DeferredItem<AmmoBoxItem> AMMO_BOX = ITEMS.registerItem("ammo_box", AmmoBoxItem::new);
    public static DeferredItem<TargetMinecartItem> TARGET_MINECART = ITEMS.registerItem("target_minecart", TargetMinecartItem::new);

    @SubscribeEvent
    public static void onItemRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            GunItemManager.registerGunItem(ModernKineticGunItem.TYPE_NAME, MODERN_KINETIC_GUN);
        }
    }
}
