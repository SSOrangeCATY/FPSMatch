package com.tacz.guns.client.tooltip;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.inventory.tooltip.AttachmentItemTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ClientAttachmentItemTooltip implements ClientTooltipComponent {
    private static final int MAX_COLUMNS = 9;
    private static final int MAX_VISIBLE_GUNS = 54;
    private static final Cache<Identifier, List<ItemStack>> CACHE = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.SECONDS).build();
    private final Identifier attachmentId;
    private List<ItemStack> showGuns = Lists.newArrayList();

    public ClientAttachmentItemTooltip(AttachmentItemTooltip tooltip) {
        this.attachmentId = tooltip.getAttachmentId();
        this.getShowGuns();
    }

    private static List<ItemStack> getAllAllowGuns(List<ItemStack> output, Identifier attachmentId) {
        ItemStack attachment = AttachmentItemBuilder.create().setId(attachmentId).build();
        TimelessAPI.getAllCommonGunIndex().forEach(entry -> {
            Identifier gunId = entry.getKey();
            ItemStack gun = GunItemBuilder.create().setId(gunId).build();
            if (!(gun.getItem() instanceof IGun iGun)) {
                return;
            }
            if (iGun.allowAttachment(gun, attachment)) {
                output.add(gun);
            }
        });
        return output;
    }

    @Override
    public int getHeight(Font font) {
        if (!hasShiftDown()) {
            return 0;
        }
        int visible = Math.min(showGuns.size(), MAX_VISIBLE_GUNS);
        int rows = Math.max(1, (visible + MAX_COLUMNS - 1) / MAX_COLUMNS);
        return rows * 18 + 2;
    }

    @Override
    public int getWidth(Font font) {
        if (!hasShiftDown()) {
            return 0;
        }
        return MAX_COLUMNS * 18;
    }

    @Override
    public void extractText(GuiGraphicsExtractor graphics, Font font, int pX, int pY) {
    }

    @Override
    public void extractImage(Font font, int mouseX, int mouseY, int width, int height, GuiGraphicsExtractor gui) {
        if (!hasShiftDown()) {
            return;
        }
        int visible = Math.min(showGuns.size(), MAX_VISIBLE_GUNS);
        for (int i = 0; i < visible; i++) {
            ItemStack stack = showGuns.get(i);
            int x = i % MAX_COLUMNS * 18 + 1;
            int y = i / MAX_COLUMNS * 18 + 1;
            TooltipIconRenderer.drawSlotIcon(gui, stack, mouseX + x, mouseY + y);
        }
    }

    private static boolean hasShiftDown() {
        return Minecraft.getInstance().hasShiftDown();
    }

    private void getShowGuns() {
        try {
            this.showGuns = CACHE.get(attachmentId, () -> getAllAllowGuns(Lists.newArrayList(), attachmentId));
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

}
