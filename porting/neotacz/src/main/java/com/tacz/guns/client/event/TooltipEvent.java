package com.tacz.guns.client.event;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.tooltip.AttachmentTooltipTextBuilder;
import net.neoforged.fml.common.EventBusSubscriber;

import com.tacz.guns.api.item.nbt.AmmoItemDataAccessor;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.client.tooltip.GunTooltipTextBuilder;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.item.GunSmithTableItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;


@EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public class TooltipEvent {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        addTaczBodyText(event);
        if (event.getFlags().isAdvanced() && RenderConfig.ENABLE_TACZ_ID_IN_TOOLTIP.get()) {
            if (event.getItemStack().getItem() instanceof GunItemDataAccessor item) {
                event.getToolTip().add(formatTooltip(GunItemDataAccessor.GUN_ID_TAG, item.getGunId(event.getItemStack())));
            } else if (event.getItemStack().getItem() instanceof AmmoItemDataAccessor item) {
                event.getToolTip().add(formatTooltip(AmmoItemDataAccessor.AMMO_ID_TAG, item.getAmmoId(event.getItemStack())));
            } else if (event.getItemStack().getItem() instanceof AttachmentItemDataAccessor item) {
                event.getToolTip().add(formatTooltip(AttachmentItemDataAccessor.ATTACHMENT_ID_TAG, item.getAttachmentId(event.getItemStack())));
            } else if (event.getItemStack().getItem() instanceof BlockItemDataAccessor item && !ModItems.GUN_SMITH_TABLE.get().equals(item)) {
                event.getToolTip().add(formatTooltip(BlockItemDataAccessor.BLOCK_ID, item.getBlockId(event.getItemStack())));
            }
        }
    }

    private static void addTaczBodyText(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof IGun iGun) {
            TimelessAPI.getCommonGunIndex(iGun.getGunId(stack))
                    .ifPresent(gunIndex -> GunTooltipTextBuilder.appendGunText(stack, iGun, gunIndex, event.getToolTip()::add));
            return;
        }
        if (stack.getItem() instanceof GunSmithTableItem tableItem) {
            GunTooltipTextBuilder.appendBlockText(tableItem.getBlockId(stack), event.getToolTip()::add);
            return;
        }
        if (stack.getItem() instanceof IAttachment iAttachment) {
            AttachmentTooltipTextBuilder.appendAttachmentText(
                    stack,
                    iAttachment.getAttachmentId(stack),
                    iAttachment.getType(stack),
                    event.getToolTip()::add
            );
        }
    }

    public static Component formatTooltip(String key, Identifier value) {
        return Component.literal(String.format("%s: \"%s\"", key, value)).withStyle(ChatFormatting.DARK_GRAY);
    }
}
