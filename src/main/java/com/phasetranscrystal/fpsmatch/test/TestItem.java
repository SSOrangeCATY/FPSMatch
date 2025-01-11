package com.phasetranscrystal.fpsmatch.test;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.client.screen.CSGameShopScreen;
import com.phasetranscrystal.fpsmatch.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.effect.FlashBlindnessMobEffect;
import com.phasetranscrystal.fpsmatch.net.FlashBombAddonS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

public class TestItem extends Item {
    public TestItem(Properties pProperties) {
        super(pProperties);
    }
    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        if(pLevel.isClientSide){
            try{
               icyllis.modernui.mc.forge.MuiForgeApi.openScreen(CSGameShopScreen.getInstance(false));

            }catch (Exception e){
                throw new RuntimeException(e);
            }
        }
        return super.use(pLevel,pPlayer,pUsedHand);
    }
}
