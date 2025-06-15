package com.phasetranscrystal.fpsmatch.common.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.phasetranscrystal.fpsmatch.common.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.common.client.shop.ClientShopSlot;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import icyllis.modernui.mc.MinecraftSurfaceView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;


public class ShopSlotRenderer implements MinecraftSurfaceView.Renderer {
    public final ItemType type;
    public final int index;
    public int width;
    public int height;
    public float scale = 1;

    public ShopSlotRenderer(ItemType type, int index) {
        this.type = type;
        this.index = index;
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void onDraw(@NotNull GuiGraphics gr, int mouseX, int mouseY, float deltaTick, double guiScale, float alpha) {
        ClientShopSlot currentSlot = ClientData.getSlotData(this.type, this.index);
        ItemStack itemStack = currentSlot.itemStack();
        boolean enable = ClientData.getMoney() >= currentSlot.cost() && !itemStack.isEmpty() && !currentSlot.isLocked();
        gr.pose().pushPose();
        Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(itemStack);
        // gr.fill(0,0,1920,1080, RenderUtil.color(124,66,232));
        if(display.isPresent()){
            float offset = 0;
            if (itemStack.getItem() instanceof IGun iGun){
                Optional<GunTabType> type = FPSMUtil.getGunTypeByGunId(iGun.getGunId(itemStack));
                if(type.isPresent() && type.get() == GunTabType.PISTOL){
                    offset = 10f;
                }
            }
            this.renderIcon(gr,display.get(),enable,offset);
        }else{
            this.renderItem(gr,itemStack,enable);
        }
        gr.pose().popPose();
    }

    public void renderItem(GuiGraphics gr,ItemStack itemStack,boolean enable){
        this.setItemColor(gr,enable);
        gr.pose().scale(scale,scale,scale);
        gr.renderItem(itemStack, 2, 0);
    }

    public void renderIcon(GuiGraphics gr,GunDisplayInstance display,boolean enable,float offset){
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        this.setIconColor(gr,enable);
        gr.blit(display.getHUDTexture(),0, (int) (8*scale),offset*scale, (float) 0, (int) (58.5F*scale), (int) (19.5F*scale), (int) (58.5F*scale), (int) (19.5F*scale));
    }

    public void setIconColor(GuiGraphics gr,boolean enable){
        if(enable){
            if(ClientData.currentTeam.equals("ct")){
                gr.setColor((float) 150 / 255, (float) 200 / 255, (float) 250 / 255,1);
            }else{
                gr.setColor((float) 234 / 255, (float) 192 /255, (float) 85 /255,1);
            }
        }else{
            gr.setColor(125 / 255F,125 / 255F,125 / 255F,1);
        }
    }

    public void setItemColor(GuiGraphics gr,boolean enable){
        if(!enable){
            gr.setColor(125 / 255F,125 / 255F,125 / 255F,1);
        }else{
            gr.setColor(1,1,1,1);
        }
    }

    public void setScale(float scale) {
        this.scale = scale;
    }
}
