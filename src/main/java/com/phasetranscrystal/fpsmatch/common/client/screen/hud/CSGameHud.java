package com.phasetranscrystal.fpsmatch.common.client.screen.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.phasetranscrystal.fpsmatch.common.client.screen.hud.animation.EnderKillAnimator;
import com.phasetranscrystal.fpsmatch.common.client.screen.hud.animation.KillAnimator;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.resource.pojo.display.gun.AmmoCountStyle;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
public class CSGameHud implements IHudRenderer {

    public static final CSGameHud INSTANCE = new CSGameHud();
    private static final ResourceLocation SEMI = new ResourceLocation("tacz", "textures/hud/fire_mode_semi.png");
    private static final ResourceLocation AUTO = new ResourceLocation("tacz", "textures/hud/fire_mode_auto.png");
    private static final ResourceLocation BURST = new ResourceLocation("tacz", "textures/hud/fire_mode_burst.png");
    private static final int MOVE_DURATION = 500; // 移动动画时长（毫秒）
    private static final int FADE_DURATION = 500; // 淡出动画时长（毫秒）
    private static final int SELECTED_BG_COLOR = RenderUtil.color(255,255,255,65); // 选中时的背景颜色（半透明白）
    private final Animation[] slotAnimations = new Animation[9]; // 扩展到7个槽位
    private KillAnimator killAnimator = new EnderKillAnimator();
    private boolean isStarted = false;
    public CSGameHud(){
        for (int i = 0; i < 9; i++) {
            slotAnimations[i] = new Animation();
        }
    }

    public void setKillAnimator(KillAnimator killAnimator) {
        if(killAnimator == null) return;
        this.killAnimator = killAnimator;
    }

    public void addKill(){
        if(killAnimator.isActive() || isStarted){
            killAnimator.addKill();
        }else{
            killAnimator.start();
            isStarted = true;
        }
    }

    public void stopKillAnim(){
        killAnimator.reset();
        isStarted = false;
    }


    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        renderInfoLine(mc,gui, guiGraphics, screenWidth, screenHeight);
        renderItemBar(mc,gui, guiGraphics, screenWidth, screenHeight);
    }


    public void renderInfoLine(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        int lineWidth = (int) (screenWidth * 0.26);
        int lineHeight = 1;
        int bottomMargin = (int) (screenHeight * 0.046);
        int fadeWidth = (int) (screenWidth * 0.026);

        int centerX = screenWidth / 2;
        int y = screenHeight - bottomMargin;

        for (int x = -lineWidth / 2; x <= lineWidth / 2; x++) {
            int alpha = 255;
            if (x < -lineWidth / 2 + fadeWidth) {
                alpha = (int) (255 * (x + (float) lineWidth / 2) / (float) fadeWidth);
            } else if (x > lineWidth / 2 - fadeWidth) {
                alpha = (int) (255 * ((float) lineWidth / 2 - x) / (float) fadeWidth);
            }
            int color = (alpha << 24) | 0xFFFFFF;
            guiGraphics.fill(centerX + x, y, centerX + x + 1, y + lineHeight, color);
        }

        // TODO renderArmorBar(mc,gui, guiGraphics, screenWidth, screenHeight);
        renderHealthBar(mc,gui, guiGraphics, centerX,lineWidth,y);
        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();
            ItemStack selectItem = mc.player.getInventory().getItem(inv.selected);
            if(selectItem.getItem() instanceof IGun iGun){
                renderGunInfo(mc,gui, guiGraphics, screenWidth, screenHeight, selectItem, iGun, centerX,lineWidth,y);
            }
        }

        renderCombatKillTips(mc,gui, guiGraphics,centerX,y);
    }

    public void renderHealthBar(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, int centerX, int lineWidth, int y) {
        LocalPlayer player = mc.player;
        if (player != null) {
            int health = (int) player.getHealth();
            int maxHealth = (int) player.getMaxHealth();
            float healthPercent = (float) health / maxHealth;
            Font font = mc.font;

            // Render health number
            int tempWidth = font.width("000") * 2;
            String healthText = String.valueOf((int) (healthPercent * 100));
            int healthTextX = centerX - lineWidth / 2 - 10 - tempWidth ;

            int healthTextY = y - font.lineHeight + 1;
            int healthBarY = y + font.lineHeight;
            int healthBarHeight = 3;
            int healthBarFillWidth = (int) (healthPercent * tempWidth);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(healthTextX + (float) tempWidth / 2 - (float) font.width(healthText), healthTextY,0);
            guiGraphics.pose().scale(2,2,0);
            guiGraphics.drawString(font, healthText, 0, 0, 0xFFFFFFFF, false);
            guiGraphics.pose().popPose();

            // Render health bar
            guiGraphics.fill(healthTextX, healthBarY, healthTextX + tempWidth, healthBarY + healthBarHeight, 0x80000000); // Background
            guiGraphics.fill(healthTextX, healthBarY, healthTextX + healthBarFillWidth, healthBarY + healthBarHeight, 0x8000FF00); // Fill
        }
    }

    public void renderArmorBar(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        //TODO WIP
    }

    private void renderGunInfo(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, int screenWidth, int screenHeight, ItemStack stack, IGun iGun,int centerX, int lineWidth, int y) {
        ResourceLocation var27 = iGun.getGunId(stack);
        GunData gunData = TimelessAPI.getClientGunIndex(var27).map(ClientGunIndex::getGunData).orElse(null);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (gunData == null || display == null || mc.player == null) return;

        int cacheMaxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData);
        int ammoCount = iGun.getCurrentAmmoCount(stack) + (iGun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT ? 1 : 0);
        int cacheInventoryAmmoCount = 0;
        String currentAmmoCountText;
        if (display.getAmmoCountStyle() == AmmoCountStyle.PERCENT) {
            currentAmmoCountText = String.valueOf((float)ammoCount / (cacheMaxAmmoCount == 0 ? 1.0F : (float)cacheMaxAmmoCount));
        } else {
            currentAmmoCountText = String.valueOf(ammoCount);
        }

        Inventory inventory = mc.player.getInventory();
        FireMode fireMode = IGun.getMainhandFireMode(mc.player);
        ResourceLocation fireModeTexture = switch (fireMode) {
            case AUTO -> AUTO;
            case BURST -> BURST;
            default -> SEMI;
        };

        if (IGunOperator.fromLivingEntity(mc.player).needCheckAmmo()) {
            if (iGun.useDummyAmmo(stack)) {
                cacheInventoryAmmoCount = iGun.getDummyAmmoAmount(stack);
            } else {
                for(int i = 0; i < inventory.getContainerSize(); ++i) {
                    ItemStack inventoryItem = inventory.getItem(i);
                    Item var5 = inventoryItem.getItem();
                    if (var5 instanceof IAmmo iAmmo) {
                        if (iAmmo.isAmmoOfGun(stack, inventoryItem)) {
                            cacheInventoryAmmoCount += inventoryItem.getCount();
                        }
                    }

                    var5 = inventoryItem.getItem();
                    if (var5 instanceof IAmmoBox iAmmoBox) {
                        if (iAmmoBox.isAmmoBoxOfGun(stack, inventoryItem)) {
                            if (iAmmoBox.isAllTypeCreative(inventoryItem) || iAmmoBox.isCreative(inventoryItem)) {
                                cacheInventoryAmmoCount = 9999;
                                break;
                            }

                            cacheInventoryAmmoCount += iAmmoBox.getAmmoCount(inventoryItem);
                        }
                    }
                }
            }
        } else {
            cacheInventoryAmmoCount = 9999;
        }
        String inventoryAmmoCountText = String.valueOf(cacheInventoryAmmoCount);
        Font font = mc.font;
        int tempWidth = font.width(currentAmmoCountText) * 2;
        int invAmmoTextX = centerX + lineWidth / 2 + 10;
        int textY = y - font.lineHeight + 1;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(invAmmoTextX, textY, 0);
        guiGraphics.pose().scale(2,2,0);
        guiGraphics.drawString(font, currentAmmoCountText, 0, 0, ammoCount == 0 ? 0xFFFF0000 : 0xFFFFFFFF, false);
        guiGraphics.pose().popPose();

        int sY = y - (font.lineHeight / 2);
        int ttt = invAmmoTextX + tempWidth + 5;

        guiGraphics.fill(ttt, sY - 1, ttt + 1, sY + font.lineHeight + 1, 0xFFFFFFFF);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(ttt + 3.5, y - (font.lineHeight * 1.5F / 2) + 0.5F, 0);
        guiGraphics.pose().scale(1.5F,1.5F,0);
        guiGraphics.drawString(font, inventoryAmmoCountText, 0, 0, cacheInventoryAmmoCount == 0 ? 0xFFFF0000 : 0xFFFFFFFF, false);
        guiGraphics.pose().popPose();


        guiGraphics.pose().pushPose();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.pose().translate(ttt + font.width(inventoryAmmoCountText) * 1.5 + 5.5, y - 4.5F, 0);
        guiGraphics.blit(fireModeTexture, 0, 0, 0.0F, 0.0F, 10, 10, 10, 10);
        guiGraphics.pose().popPose();
    }



    public void renderItemBar(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (mc.player == null) return;

        // 布局参数
        final int MARGIN_RIGHT = 10;
        final int RECT_WIDTH = 40;
        final int RECT_HEIGHT = 20;
        final int SQUARE_SIZE = 20;
        final int SPACING = 5; // 竖排槽位之间的间距
        final int TEXT_COLOR = 0xFFFFFFFF;
        final int MAX_OFFSET = 15;
        final int MOVE_DURATION = 250;

        Player player = mc.player;
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        Inventory inv = player.getInventory();
        int selectedSlot = inv.selected;

        // ========== 渲染前3个竖排物品栏 ==========
        int totalRectHeight = 3 * RECT_HEIGHT + 2 * SPACING;
        int anchorY = screenH - MARGIN_RIGHT - (totalRectHeight + SPACING + SQUARE_SIZE);

        for (int i = 0; i < 3; i++) {
            Animation anim = slotAnimations[i];
            boolean isSelected = (selectedSlot == i);
            int baseX = screenW - MARGIN_RIGHT - RECT_WIDTH;
            int baseY = anchorY + i * (RECT_HEIGHT + SPACING);

            // 状态切换处理
            handleAnimationState(anim, isSelected);

            // 动画计算
            int offsetX = 0;
            int bgColor = calculateBackgroundColor(anim, isSelected, true);

            // 前3个槽位的移动动画
            if (isSelected && bgColor == SELECTED_BG_COLOR) {
                long moveElapsed = System.currentTimeMillis() - anim.moveStartTime;
                float progress = Math.min(moveElapsed / (float)MOVE_DURATION, 1.0f);
                offsetX = (int)(-MAX_OFFSET * (1 - progress));
            }

            // 实际渲染
            renderSlot(guiGraphics, font, inv, i,
                    baseX + offsetX - 3, baseY - 3,
                    RECT_WIDTH + 3, RECT_HEIGHT + 3,
                    bgColor, TEXT_COLOR);
        }

        // ========== 渲染4-9号物品栏 ==========
        int squareAreaY = anchorY + totalRectHeight + SPACING;
        int totalSquareWidth = 6 * SQUARE_SIZE + 3 * 3;
        int squareAnchorX = screenW - MARGIN_RIGHT - totalSquareWidth;

        for (int i = 3; i < 9; i++) {
            Animation anim = slotAnimations[i];
            boolean isSelected = (selectedSlot == i);
            int indexInRow = i - 3;
            int baseX = squareAnchorX + indexInRow * (SQUARE_SIZE + 3);

            // 状态切换处理
            handleAnimationState(anim, isSelected);

            // 动画计算（无偏移）
            int bgColor = calculateBackgroundColor(anim, isSelected, false);
            renderSlot(guiGraphics, font, inv, i,
                    baseX, squareAreaY,
                    SQUARE_SIZE, SQUARE_SIZE,
                    bgColor, TEXT_COLOR);
        }
    }


    // 通用状态处理方法
    private void handleAnimationState(Animation anim, boolean isSelected) {
        if (isSelected != anim.wasSelected) {
            if (isSelected) {
                anim.moveStartTime = System.currentTimeMillis();
                anim.fadeStartTime = 0;
            } else {
                anim.fadeStartTime = System.currentTimeMillis();
            }
            anim.wasSelected = isSelected;
        }
    }

    // 通用背景颜色计算
    private int calculateBackgroundColor(Animation anim, boolean isSelected, boolean isVerticalSlot) {
        if (isSelected) {
            // 入场动画阶段（仅竖排需要检查动画时间）
            if (isVerticalSlot) {
                long moveElapsed = System.currentTimeMillis() - anim.moveStartTime;
                if (moveElapsed < MOVE_DURATION) {
                    return SELECTED_BG_COLOR;
                }
            }
            return SELECTED_BG_COLOR; // 保持选中状态
        } else if (anim.fadeStartTime > 0) {
            // 淡出动画阶段
            long fadeElapsed = System.currentTimeMillis() - anim.fadeStartTime;
            if (fadeElapsed < FADE_DURATION) {
                float progress = fadeElapsed / (float)FADE_DURATION;
                int alpha = (int)(128 * (1 - progress));
                return (alpha << 24) | 0x00FFFFFF;
            }
            anim.fadeStartTime = 0; // 结束淡出
        }
        return 0x00000000; // 默认透明
    }

    // 通用槽位渲染方法
    private void renderSlot(GuiGraphics guiGraphics, Font font, Inventory inv, int slotIndex,
                            int x, int y, int width, int height,
                            int bgColor, int textColor) {
        // 绘制背景
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 物品渲染
        ItemStack stack = inv.getItem(slotIndex);
        int itemX = x + (width - 16) / 2;
        int itemY = y + (height - 16) / 2;
        guiGraphics.renderItem(inv.player, stack, itemX, itemY, slotIndex);
        guiGraphics.renderItemDecorations(font, stack, itemX, itemY);

        // 槽位编号
        KeyMapping keyMapping = Minecraft.getInstance().options.keyHotbarSlots[slotIndex];
        Component key = keyMapping.getKey().getDisplayName();
        guiGraphics.drawString(font, key, x + width - font.width(key) - 1, y + 1, textColor, true);
        if(slotIndex + 1 <= 3){
            // 渲染名称
            if(!stack.isEmpty() && inv.selected == slotIndex) {
                String itemName = stack.getHoverName().getString();
                float nameWidth = font.width(itemName) * 0.5F;
                float nameX = x + (width - nameWidth - 2);
                float nameY = y + height - 10 + font.lineHeight * 0.5f;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(nameX, nameY, 0);
                guiGraphics.pose().scale(0.5F, 0.5F, 0);
                guiGraphics.drawString(font, itemName, 0, 0, textColor, true);
                guiGraphics.pose().popPose();
            }
        }
    }

    public void renderCombatKillTips(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics,int centerX, int y) {
        killAnimator.render(mc, gui, guiGraphics, centerX, y);
    }

    @Override
    public void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()
                || event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()
                || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()
                || event.getOverlay() == VanillaGuiOverlay.MOUNT_HEALTH.type()
                || event.getOverlay().id().getPath().equals("tac_gun_hud_overlay")
        ){
            event.setCanceled(true);
        }
    }

    private static class Animation {
        long moveStartTime = 0;    // 入场动画开始时间
        long fadeStartTime = 0;    // 淡出动画开始时间
        boolean wasSelected = false; // 上次选中状态
    }
}
