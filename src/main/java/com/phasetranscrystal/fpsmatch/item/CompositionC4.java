package com.phasetranscrystal.fpsmatch.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.entity.CompositionC4Entity;
import com.phasetranscrystal.fpsmatch.entity.EntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;


@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
public class CompositionC4 extends Item {

    public CompositionC4(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        ItemStack itemstack = pPlayer.getItemInHand(pUsedHand);
        if(itemstack.getTag() != null && itemstack.getTag().contains("pos1") && itemstack.getTag().contains("pos2")){
            BlockPos pos1 = BlockPos.of(itemstack.getOrCreateTag().getLong("pos1"));
            BlockPos pos2 = BlockPos.of(itemstack.getOrCreateTag().getLong("pos2"));
            if (isPlayerInArea(pPlayer, pos1, pos2)) {
                pPlayer.startUsingItem(pUsedHand);
                pLevel.playSound(null, pPlayer.getX(), pPlayer.getY(), pPlayer.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.0F);
                return InteractionResultHolder.consume(itemstack);
            }else {
                pPlayer.displayClientMessage(Component.literal("You are not in the designated area!"), true);
                pLevel.playSound(null, pPlayer.getX(), pPlayer.getY(), pPlayer.getZ(), SoundEvents.CAT_PURREOW, SoundSource.PLAYERS, 1.0F, 1.0F);
                return InteractionResultHolder.pass(itemstack);
            }
        }else{
            pPlayer.displayClientMessage(Component.literal("You are not define the designated area!"), true);
            return InteractionResultHolder.pass(itemstack);
        }
    }

    private boolean isPlayerInArea(Player player, BlockPos cornerOne, BlockPos cornerTwo) {
        AABB area = new AABB(
                Math.min(cornerOne.getX(), cornerTwo.getX()),
                Math.min(cornerOne.getY(), cornerTwo.getY()),
                Math.min(cornerOne.getZ(), cornerTwo.getZ()),
                Math.max(cornerOne.getX(), cornerTwo.getX()),
                Math.max(cornerOne.getY(), cornerTwo.getY()),
                Math.max(cornerOne.getZ(), cornerTwo.getZ())
        );
        return area.contains(new Vec3(player.getX(),player.getY(),player.getZ()));
    }

    public @NotNull ItemStack finishUsingItem(ItemStack pStack, Level pLevel, LivingEntity pLivingEntity) {
        if (pLivingEntity instanceof Player player) {
            CompositionC4Entity entityC4 = new CompositionC4Entity(EntityRegister.C4.get(), pLevel);
            entityC4.setPos(player.getX(), player.getY(), player.getZ());
            pLevel.addFreshEntity(entityC4);
            if (player.isCreative()) return pStack;
        }
        return ItemStack.EMPTY;
    }


    public InteractionResult useOn(UseOnContext pContext) {
        Player player = pContext.getPlayer();
        if (player != null && player.isCreative()) {
            ItemStack itemStack = pContext.getItemInHand();
            itemStack.getOrCreateTag().putLong("pos2", pContext.getClickedPos().asLong());
            player.displayClientMessage(Component.literal("Position 2 set."), true);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }
    @Override
    public UseAnim getUseAnimation(ItemStack pStack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack itemstack) {
        if (itemstack.getTag() != null && itemstack.getTag().contains("pos1") && itemstack.getTag().contains("pos2")) {
            return 80;
        } else {
            return 0;
        }
    }
    @SubscribeEvent
    public static void onLeftClickBlockEvent(PlayerInteractEvent.LeftClickBlock event){
        if(event.getEntity() instanceof ServerPlayer player && player.isCreative() && event.getItemStack().getItem() instanceof CompositionC4){
            event.getItemStack().getOrCreateTag().putLong("pos1", event.getPos().asLong());
            player.displayClientMessage(Component.literal("Position 1 set."), true);
            event.setCanceled(true);
        }

    }


}