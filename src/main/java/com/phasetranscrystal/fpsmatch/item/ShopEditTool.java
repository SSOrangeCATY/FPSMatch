package com.phasetranscrystal.fpsmatch.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.client.screen.EditorShopContainer;
import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
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
import net.minecraftforge.common.capabilities.ICapabilityProvider;
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
        if (!pLevel.isClientSide && pPlayer instanceof ServerPlayer serverPlayer) {
            //shift 右键
            if (serverPlayer.isShiftKeyDown()) {
                mapList = FPSMCore.getInstance().getMapNames();
                String preSelectedMap, preSelectedShop, newShop;
                //设置选中的商店
                if (!mapList.isEmpty() && itemInHand.getItem() instanceof ShopEditTool iteractItem) {
                    // 是否提前设置队伍,否则使用默认值
                    if (!itemInHand.getOrCreateTag().contains(MAP_TAG)) {
                        iteractItem.setTag(itemInHand, MAP_TAG, mapList.get(0));
                    }
                    preSelectedMap = iteractItem.getTag(itemInHand, MAP_TAG);
                    BaseMap map = FPSMCore.getInstance().getMapByName(preSelectedMap);
                    if (map instanceof ShopMap<?> shopMap) {
                        shopList = shopMap.getShopNames();
                        if (!shopList.isEmpty() && itemInHand.getOrCreateTag().contains(SHOP_TAG)) {
                            preSelectedShop = iteractItem.getTag(itemInHand, SHOP_TAG);

                            int preIndex = shopList.indexOf(preSelectedShop);
                            if (preIndex == shopList.size() - 1)
                                newShop = shopList.get(0);
                            else newShop = shopList.get(preIndex + 1);
                            iteractItem.setTag(itemInHand, SHOP_TAG, newShop);
                        } else {
                            //默认商店为空不设置TAG
                            if (shopList.isEmpty()) {
                                serverPlayer.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.missing_shop").withStyle(ChatFormatting.RED));
                                return InteractionResultHolder.success(itemInHand);
                            }
                            //没有SHOP_TAG取第一个商店
                            newShop = shopList.get(0);
                            iteractItem.setTag(itemInHand, SHOP_TAG, newShop);
                        }
                        serverPlayer.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.all_shops").withStyle(ChatFormatting.BOLD)
                                .append(shopList.toString()).withStyle(ChatFormatting.GREEN)
                        );
                        BaseTeam team = map.getMapTeams().getTeamByName(newShop);
                        //加入队伍尝试
                        if (team != null && team.getRemainingLimit() >= 1) {
                            map.join(newShop, serverPlayer);
                            serverPlayer.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.select_and_join_shop").withStyle(ChatFormatting.BOLD)
                                    .append(newShop).withStyle(ChatFormatting.AQUA)
                            );
                        } else
                            serverPlayer.sendSystemMessage(Component.translatable("commands.fpsm.team.join.failure", team));

                        ;
                    }

                } else //默认地图为空
                    serverPlayer.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.missing_map").withStyle(ChatFormatting.RED));


                return InteractionResultHolder.success(itemInHand);
            }

        }
        //右键打开GUI
        if (pPlayer instanceof ServerPlayer serverPlayer) {
            if (itemInHand.getItem() instanceof ShopEditTool editTool) {
                // 服务端打开 GUI
                if (!itemInHand.getOrCreateTag().contains(SHOP_TAG)) {
                    serverPlayer.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.missing_shop").withStyle(ChatFormatting.RED));
                    return InteractionResultHolder.success(pPlayer.getItemInHand(pUsedHand));
                }
                // 服务器端调用 openScreen 方法
                NetworkHooks.openScreen(serverPlayer,
                        new SimpleMenuProvider(
                                (windowId, inv, p) -> {
                                    return new EditorShopContainer(windowId, inv, itemInHand); // 创建容器并传递物品
                                },
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
                if (!mapList.isEmpty() && event.getItemStack().getOrCreateTag().contains(MAP_TAG)) {
                    preSelectedMap = iteractItem.getTag(event.getItemStack(), MAP_TAG);
                    int preIndex = mapList.indexOf(preSelectedMap);
                    if (preIndex == mapList.size() - 1)
                        newMap = mapList.get(0);
                    else newMap = mapList.get(preIndex + 1);
                    iteractItem.setTag(event.getItemStack(), MAP_TAG, newMap);
                } else {
                    //默认地图为空，不设置TAG
                    if (mapList.isEmpty()) {
                        player.sendSystemMessage(Component.translatable("message.fpsm.shop_edit_tool.missing_map").withStyle(ChatFormatting.RED));
                        return;
                    }
                    //不存在MapTag取第一个地图
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

    //初始化库存
    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new EditorShopCapabilityProvider(stack);
    }

    //显示选择信息
    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);

        String selectedMap = getTag(pStack, MAP_TAG);
        String selectedShop = getTag(pStack, SHOP_TAG);

        // 添加分隔符
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));

        // 当前选择的地图
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.selected_map")
                .append(": ")
                .append(Component.literal(selectedMap.isEmpty() ? Component.translatable("tooltip.fpsm.none").getString() : selectedMap)
                        .withStyle(ChatFormatting.AQUA)));

        // 当前选择的商店
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.selected_shop")
                .append(": ")
                .append(Component.literal(selectedShop.isEmpty() ? Component.translatable("tooltip.fpsm.none").getString() : selectedShop)
                        .withStyle(ChatFormatting.AQUA)));

        // 再次添加分隔符
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));

        // 使用帮助提示
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.usage_info").withStyle(ChatFormatting.GRAY));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.switch_shop").withStyle(ChatFormatting.YELLOW));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.switch_map").withStyle(ChatFormatting.YELLOW));
        pTooltipComponents.add(Component.translatable("tooltip.fpsm.open_editor").withStyle(ChatFormatting.YELLOW));
    }


}
