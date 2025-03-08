package com.phasetranscrystal.fpsmatch.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.entity.BaseProjectileEntity;
import com.phasetranscrystal.fpsmatch.core.function.IHolder;
import com.phasetranscrystal.fpsmatch.core.item.IThrowEntityAble;
import com.phasetranscrystal.fpsmatch.net.ThrowEntityC2SPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BaseThrowAbleItem extends Item implements IThrowEntityAble {
    public final BiFunction<Player,Level,BaseProjectileEntity> factory;
    public final IHolder<SoundEvent> voice;
    public BaseThrowAbleItem(Properties pProperties, BiFunction<Player,Level,BaseProjectileEntity> factory) {
        super(pProperties);
        this.factory = factory;
        this.voice = null;
    }

    public BaseThrowAbleItem(Properties pProperties, BiFunction<Player,Level,BaseProjectileEntity> factory, IHolder<SoundEvent> voice) {
        super(pProperties);
        this.factory = factory;
        this.voice = voice;
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity().getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof IThrowEntityAble grenade && !event.getEntity().getCooldowns().isOnCooldown((Item) grenade)) {
            FPSMatch.INSTANCE.send(PacketDistributor.SERVER.noArg(), new ThrowEntityC2SPacket(1.5F, 1.0F));
        }
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.LeftClickBlock event) {
        if(event.getLevel().isClientSide) return;
        if (event.getEntity().getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof IThrowEntityAble iThrowEntityAble) {
            iThrowEntityAble.shoot(event.getEntity(), event.getLevel(), InteractionHand.MAIN_HAND, 1.5F, 1.0F);
        }
    }

    public @NotNull InteractionResult useOn(UseOnContext pContext) {
        Player player = pContext.getPlayer();
        if(player == null){
            return InteractionResult.PASS;
        }
        return this.use(pContext.getLevel(), player, pContext.getHand()).getResult();
    }

    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level pLevel, @NotNull Player pPlayer, @NotNull InteractionHand pHand) {
        return InteractionResultHolder.sidedSuccess(this.shoot(pPlayer, pLevel, pHand, 0.25F, 0.35F), pLevel.isClientSide());
    }

    @Override
    public BaseProjectileEntity getEntity(Player pPlayer, Level pLevel) {
        return this.factory.apply(pPlayer, pLevel);
    }

    @Override
    public SoundEvent getThrowVoice() {
        return this.voice == null ? IThrowEntityAble.super.getThrowVoice() : this.voice.get();
    }
}
