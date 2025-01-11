package com.phasetranscrystal.fpsmatch.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.entity.IncendiaryGrenadeEntity;
import com.phasetranscrystal.fpsmatch.net.ThrowIncendiaryGrenadeC2SPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class IncendiaryGrenade extends Item {
    public IncendiaryGrenade(Properties pProperties) {
        super(pProperties);
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity().getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof IncendiaryGrenade grenade && !event.getEntity().getCooldowns().isOnCooldown(grenade)) {
            FPSMatch.INSTANCE.send(PacketDistributor.SERVER.noArg(), new ThrowIncendiaryGrenadeC2SPacket(1.5F, 1.0F));
        }
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.LeftClickBlock event) {
        if(event.getLevel().isClientSide) return;
        if (event.getEntity().getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof IncendiaryGrenade grenade) {
            grenade.throwIncendiaryGrenade(event.getEntity(), event.getLevel(), InteractionHand.MAIN_HAND, 1.5F, 1.0F);
        }
    }


    public ItemStack throwIncendiaryGrenade(Player pPlayer, Level pLevel, InteractionHand pHand, float velocity, float inaccuracy) {
        ItemStack itemstack = pPlayer.getItemInHand(pHand);
        if(pPlayer.getCooldowns().isOnCooldown(this)){
            return itemstack;
        }
        pLevel.playSound(null, pPlayer.getX(), pPlayer.getY(), pPlayer.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (pLevel.getRandom().nextFloat() * 0.4F + 0.8F));
        pPlayer.getCooldowns().addCooldown(this, 20);
        if (!pLevel.isClientSide) {
            IncendiaryGrenadeEntity shell = new IncendiaryGrenadeEntity(pPlayer, pLevel);
            shell.setItem(itemstack);
            shell.shootFromRotation(pPlayer, pPlayer.getXRot(), pPlayer.getYRot(), 0.0F, velocity, inaccuracy);
            pLevel.addFreshEntity(shell);
        }

        pPlayer.awardStat(Stats.ITEM_USED.get(this));
        if (!pPlayer.getAbilities().instabuild) {
            itemstack.shrink(1);
        }
        return itemstack;
    }

    public @NotNull InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, @NotNull InteractionHand pHand) {
        return InteractionResultHolder.sidedSuccess(this.throwIncendiaryGrenade(pPlayer, pLevel, pHand, 0.25F, 0.35F), pLevel.isClientSide());
    }
}
