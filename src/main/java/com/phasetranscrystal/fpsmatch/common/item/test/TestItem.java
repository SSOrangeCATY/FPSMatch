package com.phasetranscrystal.fpsmatch.common.item.test;

import com.phasetranscrystal.fpsmatch.common.client.screen.CSGameShopScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class TestItem extends Item {
    public TestItem(Properties pProperties) {
        super(pProperties);
    }
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level pLevel, @NotNull Player pPlayer, @NotNull InteractionHand pUsedHand) {
        if(pLevel.isClientSide){
            try{
               icyllis.modernui.mc.forge.MuiForgeApi.openScreen(CSGameShopScreen.getInstance());
            }catch (Exception e){
                e.fillInStackTrace();
            }
        }
        return super.use(pLevel,pPlayer,pUsedHand);
    }
}
