package com.phasetranscrystal.fpsmatch.item;

import com.phasetranscrystal.fpsmatch.client.screen.EditorShopContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public class ShopEditTool extends Item {
    public ShopEditTool(Properties pProperties) {
        super(pProperties);

    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        if (pPlayer instanceof ServerPlayer serverPlayer) {
            // 服务端打开 GUI
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (windowId, inv, p) -> new EditorShopContainer(windowId, inv),
                    Component.literal("My GUI")
            ));
        }
        return InteractionResultHolder.success(pPlayer.getItemInHand(pUsedHand));
    }
}
