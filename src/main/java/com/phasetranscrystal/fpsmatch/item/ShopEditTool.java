package com.phasetranscrystal.fpsmatch.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.client.screen.EditorShopContainer;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShopEditTool extends Item {
    public static final String MAP_TAG = "SelectedMap";
    public static final String SHOP_TAG = "SelectedShop";
    private static List<String> mapList = new ArrayList<>();
    private static List<String> shopList = new ArrayList<>();

    public ShopEditTool(Properties pProperties) {
        super(pProperties);

    }


    public ItemStack setTag(ItemStack stack, String tagName, String value) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(tagName, value);
        return stack;
    }

    public String getTag(ItemStack stack, String tagName) {
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(tagName)) {
            return tag.getString(tagName);
        }
        return "";
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        ItemStack itemInHand = pPlayer.getItemInHand(pUsedHand);
        if (pLevel.isClientSide) {
            return InteractionResultHolder.pass(itemInHand); // 让客户端跳过
        }
        //shift 右键
        if (pPlayer.isShiftKeyDown()) {
            mapList = FPSMCore.getInstance().getMapNames();
            String preSelectedMap, preSelectedShop, newShop;
            //设置选中的商店
            if (itemInHand.getItem() instanceof ShopEditTool iteractItem) {
                // 是否提前设置队伍,否则使用默认值
                if (!itemInHand.getOrCreateTag().contains(MAP_TAG)) {
                    iteractItem.setTag(itemInHand, MAP_TAG, mapList.get(0));
                }
                preSelectedMap = iteractItem.getTag(itemInHand, MAP_TAG);
                if (FPSMCore.getInstance().getMapByName(preSelectedMap) instanceof ShopMap<?> map) {
                    shopList = map.getShopNames();
                    if (itemInHand.getOrCreateTag().contains(SHOP_TAG)) {
                        preSelectedShop = iteractItem.getTag(itemInHand, SHOP_TAG);

                        int preIndex = shopList.indexOf(preSelectedShop);
                        if (preIndex == shopList.size() - 1)
                            newShop = shopList.get(0);
                        else newShop = shopList.get(preIndex + 1);
                        iteractItem.setTag(itemInHand, SHOP_TAG, newShop);
                    } else {
                        //默认商店为空
                        if (shopList.isEmpty()) {
                            pPlayer.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.missing_shop").withStyle(ChatFormatting.RED));
                            return InteractionResultHolder.success(itemInHand);
                        }
                        newShop = shopList.get(0);
                        iteractItem.setTag(itemInHand, SHOP_TAG, newShop);
                    }
                    pPlayer.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.all_shops").withStyle(ChatFormatting.BOLD)
                            .append(shopList.toString()).withStyle(ChatFormatting.GREEN)
                    );
                    pPlayer.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.select_shop").withStyle(ChatFormatting.BOLD)
                            .append(newShop).withStyle(ChatFormatting.AQUA)
                    );
                }

            }


            return InteractionResultHolder.success(itemInHand);
        }


        if (pPlayer instanceof ServerPlayer serverPlayer) {
            if (itemInHand.getItem() instanceof ShopEditTool editTool) {
                // 服务端打开 GUI
                if (!itemInHand.getOrCreateTag().contains(SHOP_TAG)) {
                    return InteractionResultHolder.success(pPlayer.getItemInHand(pUsedHand));
                }
                // 服务器端调用 openScreen 方法
                NetworkHooks.openScreen(serverPlayer,
                        new SimpleMenuProvider(
                                (windowId, inv, p) -> new EditorShopContainer(windowId, inv, itemInHand), // 创建容器并传递物品
                                Component.translatable("gui.fpsm.shop_editor.title")
                        ),
                        buf -> buf.writeItem(itemInHand)  // 将物品写入缓冲区
                );

            }

        }
        return InteractionResultHolder.success(pPlayer.getItemInHand(pUsedHand));
    }

    //处理shift左键事件
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        mapList = FPSMCore.getInstance().getMapNames();
        String preSelectedMap, newMap;
        Player player = event.getEntity();

        if (player.isShiftKeyDown()) {
            //设置选中的地图
            if (event.getItemStack().getItem() instanceof ShopEditTool iteractItem) {
                if (event.getItemStack().getOrCreateTag().contains(MAP_TAG)) {
                    preSelectedMap = iteractItem.getTag(event.getItemStack(), MAP_TAG);
                    int preIndex = mapList.indexOf(preSelectedMap);
                    if (preIndex == mapList.size() - 1)
                        newMap = mapList.get(0);
                    else newMap = mapList.get(preIndex + 1);
                    iteractItem.setTag(event.getItemStack(), MAP_TAG, newMap);
                } else {
                    //默认地图为空
                    if (mapList.isEmpty()) {
                        player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.missing_map").withStyle(ChatFormatting.RED));
                        return;
                    }
                    newMap = mapList.get(0);
                    iteractItem.setTag(event.getItemStack(), MAP_TAG, newMap);
                }
                player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.all_maps").withStyle(ChatFormatting.BOLD)
                        .append(mapList.toString()).withStyle(ChatFormatting.GREEN)
                );
                player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.select_map").withStyle(ChatFormatting.BOLD)
                        .append(newMap).withStyle(ChatFormatting.AQUA)
                );
            }
        }
    }

    //显示选择信息
    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
    }
}
