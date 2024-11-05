package com.phasetranscrystal.fpsmatch.test;

import com.phasetranscrystal.fpsmatch.client.CSGameShopScreen;
import icyllis.modernui.mc.forge.MuiForgeApi;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TestItem extends Item {
    public TestItem(Properties pProperties) {
        super(pProperties);
    }
    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        if(pLevel.isClientSide){
            MuiForgeApi.openScreen(new CSGameShopScreen(false));
        }
        return super.use(pLevel,pPlayer,pUsedHand);
    }
}
