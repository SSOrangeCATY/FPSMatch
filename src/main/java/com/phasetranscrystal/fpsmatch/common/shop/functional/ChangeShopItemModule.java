package com.phasetranscrystal.fpsmatch.common.shop.functional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.persistence.SaveHolder;
import com.phasetranscrystal.fpsmatch.common.event.register.RegisterFPSMSaveDataEvent;
import com.phasetranscrystal.fpsmatch.core.shop.event.ShopSlotChangeEvent;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.compat.gun.GunCompatManager;
import com.phasetranscrystal.fpsmatch.compat.gun.IGunProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 鏀瑰彉鍟嗗簵鐗╁搧鐨勭洃鍚ā鍧椼€? * <p>
 * 璇ユā鍧楃敤浜庡湪鍟嗗簵妲戒綅鍙樻洿浜嬩欢涓姩鎬佷慨鏀规Ы浣嶇殑鐗╁搧鍜屼环鏍笺€? * 鏀寔鍦ㄨ喘涔版椂鏇挎崲鐗╁搧鍜屼环鏍硷紝骞跺湪閫€鍥炴椂鎭㈠榛樿璁剧疆銆? */
@net.neoforged.fml.common.EventBusSubscriber(modid = FPSMatch.MODID)
public record ChangeShopItemModule(ItemStack defaultItem, int defaultCost, ItemStack changedItem, int changedCost) implements ListenerModule {
    /**
     * 璇ユā鍧楃殑缂栬В鐮佸櫒锛岀敤浜庡簭鍒楀寲鍜屽弽搴忓垪鍖栥€?     */
    public static final Codec<ChangeShopItemModule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("defaultItem").forGetter(ChangeShopItemModule::defaultItem),
            Codec.INT.fieldOf("defaultCost").forGetter(ChangeShopItemModule::defaultCost),
            ItemStack.CODEC.fieldOf("changedItem").forGetter(ChangeShopItemModule::changedItem),
            Codec.INT.fieldOf("changedCost").forGetter(ChangeShopItemModule::changedCost)
    ).apply(instance, ChangeShopItemModule::new));

    /**
     * 娉ㄥ唽璇ユā鍧楀埌鐩戝惉妯″潡绠＄悊鍣ㄣ€?     */
    public void read() {
        FPSMCore.getInstance().getListenerModuleManager().addListenerType(this);
    }

    /**
     * 澶勭悊鍟嗗簵妲戒綅鍙樻洿浜嬩欢銆?     * <p>
     * 濡傛灉妲戒綅宸茶喘涔颁笖涓嶇鍚堥€€鍥炴潯浠讹紝鍒欎笉鎵ц浠讳綍鎿嶄綔銆?     * 濡傛灉浜嬩欢鏍囧織涓烘锛屽垯灏嗘Ы浣嶇殑鐗╁搧鍜屼环鏍兼浛鎹负鏂拌缃€?     * 濡傛灉浜嬩欢鏍囧織涓鸿礋锛屽垯灏嗘Ы浣嶇殑鐗╁搧鍜屼环鏍兼仮澶嶄负榛樿璁剧疆銆?     *
     * @param event 鍟嗗簵妲戒綅鍙樻洿浜嬩欢
     */
    @Override
    public void onChange(ShopSlotChangeEvent event) {
        if (event.shopSlot.getBoughtCount() > 0 && !event.shopSlot.returningChecker.test(changedItem)) return;
        if (event.flag > 0) {
            event.shopSlot.itemSupplier = changedItem::copy;
            event.shopSlot.setCost(changedCost);
        } else if (event.flag < 0) {
            event.shopSlot.returnItem(event.player);
            event.addMoney(event.shopSlot.getCost());
            event.shopSlot.itemSupplier = defaultItem::copy;
            event.shopSlot.setCost(defaultCost);
        }
    }


    @Override
    public void onReset(ShopSlot slot){
        slot.itemSupplier = defaultItem::copy;
        slot.setCost(defaultCost);
    }
    /**
     * 鑾峰彇璇ユā鍧楃殑鍚嶇О銆?     * <p>
     * 濡傛灉淇敼鍚庣殑鐗╁搧鏄灙姊帮紝鍒欎娇鐢ㄦ灙姊扮殑 ID 浣滀负鍚嶇О銆?     * 鍚﹀垯锛屼娇鐢ㄩ粯璁ょ墿鍝佺殑娉ㄥ唽鍚嶇О浣滀负鍚嶇О銆?     *
     * @return 妯″潡鍚嶇О
     */
    @Override
    public String getName() {
        String name;
        IGunProvider provider = GunCompatManager.findProvider(this.changedItem);
        if(provider.isGun(this.changedItem)){
            name = provider.getGunId(this.changedItem).toString().replace(":","_");
        }else{
            name = BuiltInRegistries.ITEM.getKey(this.defaultItem.getItem()).toString().replace(":","_");
        }
        return "changeItem_" + name;
    }

    /**
     * 鑾峰彇璇ユā鍧楃殑浼樺厛绾с€?     * @return 妯″潡浼樺厛绾?     */
    @Override
    public int getPriority() {
        return 1;
    }


    @SubscribeEvent
    public static void onDataRegister(RegisterFPSMSaveDataEvent event) {
        event.registerData(ChangeShopItemModule.class, "ListenerModule", new SaveHolder.Builder<>(ChangeShopItemModule.CODEC)
                .withLoadHandler(ChangeShopItemModule::read)
                .withSaveHandler((manager) ->
                        FPSMCore.getInstance().getListenerModuleManager().getRegistry().forEach((name, module) -> {
                            if (module instanceof ChangeShopItemModule cSIM) {
                                manager.saveData(cSIM, cSIM.getName(),true);
                            }
                        })
                ).build());
    }
}
