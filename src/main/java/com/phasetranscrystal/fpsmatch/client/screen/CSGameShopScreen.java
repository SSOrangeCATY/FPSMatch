package com.phasetranscrystal.fpsmatch.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import com.phasetranscrystal.fpsmatch.client.shop.ClientShopSlot;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.ShopAction;
import com.phasetranscrystal.fpsmatch.net.ShopActionC2SPacket;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import icyllis.modernui.R;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.RelativeLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;


public class CSGameShopScreen extends Fragment implements ScreenCallback{
    public static final String BROKEN_ICON = "gun/hud/broken.png";
    public static final String ITEM_ICON = "gun/hud/item.png";
    public static final Map<ItemType, List<GunButtonLayout>> shopButtons = new HashMap<>();
    public static final String BACKGROUND = "ui/cs/background.png";
    public static final int DISABLE_TEXTURE_COLOR = RenderUtil.color(65,65,65);
    public static final int DISABLE_TEXT_COLOR = RenderUtil.color(100,100,100);
    private static final String[] TOP_NAME_KEYS = new String[]{"fpsm.shop.title.equipment","fpsm.shop.title.pistol","fpsm.shop.title.mid_rank","fpsm.shop.title.rifle","fpsm.shop.title.throwable"};
    public static boolean refreshFlag = false;
    public static boolean debug;
    private static CSGameShopScreen INSTANCE;
    private RelativeLayout window = null;

    public CSGameShopScreen(boolean debug){
        CSGameShopScreen.debug = debug;
    }

    public static CSGameShopScreen getInstance(boolean debug) {
        if(INSTANCE == null) {
            INSTANCE = new CSGameShopScreen(debug);
        }
        return INSTANCE;
    }

    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        if (window == null) {
            window = new WindowLayout(getContext());
        }
        return window;
    }

    public static class WindowLayout extends RelativeLayout {
        private float scale = calculateScaleFactor(this.getWidth(),this.getHeight());
        // main
        private ImageView background;
        private RelativeLayout headBar;
        private LinearLayout content;

        // header bar start
        public TextView moneyText;
        public TextView cooldownText;
        public TextView nextRoundMinMoneyText;
        // end
        // ----------------------------------------------------
        // content start
        public LinearLayout shopWindow;
        public List<TypeBarLayout> typeBarLayouts = new ArrayList<>();
        // end

        public WindowLayout(Context context) {
            super(context);
            initializeLayout();
        }

        private void initializeLayout() {
            content = new LinearLayout(getContext());
            content.setOrientation(LinearLayout.HORIZONTAL);
            background = new ImageView(getContext());
            ImageDrawable imageDrawable = new ImageDrawable(Image.create(FPSMatch.MODID, BACKGROUND));
            imageDrawable.setAlpha(60);

            background.setImageDrawable(imageDrawable);
            background.setScaleType(ImageView.ScaleType.FIT_CENTER);

            shopWindow = new LinearLayout(this.getContext());
            for (int i = 0; i < 5; i++) {
                TypeBarLayout typeBar = new TypeBarLayout(this.getContext(),i);
                shopWindow.addView(typeBar, new LinearLayout.LayoutParams(dp((TypeBarLayout.getGunButtonWeight(i) + 30) * scale), -1));
                typeBarLayouts.add(typeBar);
            }
            content.addView(shopWindow, new LinearLayout.LayoutParams(dp(950 * scale), dp(550* scale)));

            RelativeLayout.LayoutParams shopWindowParams = new RelativeLayout.LayoutParams(
                    dp(950 * scale),
                    dp(550 * scale));
            shopWindowParams.setMargins(dp(240 * scale), dp(210 * scale), 0, 0);

            RelativeLayout.LayoutParams shopWindowBackGroundParams = new RelativeLayout.LayoutParams(
                    dp(950 * scale),
                    dp(550 * scale));
            shopWindowBackGroundParams.setMargins(dp(240 * scale), dp(208 * scale), 0, 0);

            // HEAD BAR START
            headBar = new RelativeLayout(getContext());
            RelativeLayout.LayoutParams titleBarParams = new RelativeLayout.LayoutParams(dp(scale *950), dp(scale*38));
            titleBarParams.setMargins(dp(scale *240), dp(scale*170), 0, 0);

            ImageView titleBarBackground = new ImageView(getContext());
            titleBarBackground.setImageDrawable(imageDrawable);
            titleBarBackground.setScaleType(ImageView.ScaleType.FIT_XY);
            headBar.addView(titleBarBackground);
            moneyText = new TextView(getContext());
            RelativeLayout.LayoutParams moneyParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            moneyParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            moneyParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            moneyParams.setMargins((int) (25* scale),0,0,0);
            moneyText.setLayoutParams(moneyParams);
            moneyText.setTextColor(ClientData.currentTeam.equals("ct") ? RenderUtil.color(150,200,250) : RenderUtil.color(234, 192, 85));
            moneyText.setTextSize(18);

            cooldownText = new TextView(getContext());
            RelativeLayout.LayoutParams cooldownParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            cooldownParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            cooldownText.setText(I18n.get("fpsm.shop.title.cooldown","?"));
            cooldownText.setLayoutParams(cooldownParams);
            cooldownText.setTextSize(18);

            nextRoundMinMoneyText = new TextView(getContext());
            RelativeLayout.LayoutParams minmoneyText = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            minmoneyText.addRule(RelativeLayout.CENTER_IN_PARENT);
            minmoneyText.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            //ClientData.getNextRoundMinMoney()
            nextRoundMinMoneyText.setText(I18n.get("fpsm.shop.title.min.money", "?"));
            minmoneyText.setMargins(0,0, (int) (20* scale),0);
            nextRoundMinMoneyText.setLayoutParams(minmoneyText);
            nextRoundMinMoneyText.setTextSize(15);
            headBar.addView(moneyText);
            headBar.addView(cooldownText);
            headBar.addView(nextRoundMinMoneyText);
            //END

            addView(headBar, titleBarParams);
            addView(background, shopWindowBackGroundParams);
            addView(content, shopWindowParams);
        }

        private float calculateScaleFactor(int w, int h) {
           return Math.min((float) w / 1920,(float) h / 1080);
        }

        @Override
        protected void onSizeChanged(int width, int height, int prevWidth, int prevHeight) {
                scale = calculateScaleFactor(width, height);

                RelativeLayout.LayoutParams shopWindowParams = new RelativeLayout.LayoutParams(
                        dp(950 * scale),
                        dp(550 * scale));
                shopWindowParams.setMargins(dp(240 * scale),dp(210 * scale), 0, 0);

                RelativeLayout.LayoutParams shopWindowBackGroundParams = new RelativeLayout.LayoutParams(
                        dp(950 * scale),
                        dp(550 * scale));
                shopWindowBackGroundParams.setMargins(dp(240 * scale), dp(208 * scale), 0, 0);

                this.content.setLayoutParams(shopWindowParams);
                this.background.setLayoutParams(shopWindowBackGroundParams);

                RelativeLayout.LayoutParams titleBarParams = new RelativeLayout.LayoutParams(dp(scale*950), dp(scale*38));
                titleBarParams.setMargins(dp(scale * 240), dp(scale * 170), 0, 0);
                this.headBar.setLayoutParams(titleBarParams);

                RelativeLayout.LayoutParams minmoneyText = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                minmoneyText.addRule(RelativeLayout.CENTER_IN_PARENT);
                minmoneyText.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                minmoneyText.setMargins(0,0, (int) (20 * scale),0);
                nextRoundMinMoneyText.setLayoutParams(minmoneyText);
                nextRoundMinMoneyText.setTextSize(15 * scale);

                RelativeLayout.LayoutParams moneyParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                moneyParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                moneyParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                moneyParams.setMargins((int) (25 * scale),0,0,0);
                moneyText.setLayoutParams(moneyParams);
                moneyText.setTextSize(18 * scale);

                shopWindow.setLayoutParams(new LinearLayout.LayoutParams(dp(950 * scale), dp(550* scale)));

                cooldownText.setTextSize(18* scale);

                typeBarLayouts.forEach(typeBarLayout -> typeBarLayout.setScale(scale));

                shopButtons.forEach((type,gunButtons)-> gunButtons.forEach(gunButtonLayout -> gunButtonLayout.setScale(scale)));
        }
        @Override
        public void draw(@NotNull Canvas canvas) {
            super.draw(canvas);
            updateText();
        }

        public void updateText(){
            moneyText.setText("$ "+ClientData.getMoney());
            moneyText.setTextColor(ClientData.currentTeam.equals("ct") ? RenderUtil.color(150,200,250) : RenderUtil.color(234, 192, 85));
        }

    }

    public static class TypeBarLayout extends LinearLayout {
        int i;
        LinearLayout titleBar;
        TextView numTab;
        TextView title;
        List<LinearLayout> guns = new ArrayList<>();
        List<LinearLayout> shops = new ArrayList<>();

        public TypeBarLayout(Context context,int i) {
            super(context);
            this.i = i;
            setOrientation(LinearLayout.VERTICAL);
            titleBar = new LinearLayout(getContext());
            int textColor = RenderUtil.color(203, 203, 203);
            numTab = new TextView(getContext());
            numTab.setTextColor(textColor);
            numTab.setText(String.valueOf(i + 1));
            numTab.setTextSize(15);
            numTab.setPadding(15, 10, 0, 0);
            numTab.setGravity(Gravity.LEFT);

            title = new TextView(getContext());
            title.setTextColor(textColor);
            title.setText(I18n.get(TOP_NAME_KEYS[i]));
            title.setTextSize(21);
            title.setGravity(Gravity.CENTER);

            titleBar.addView(numTab, new LinearLayout.LayoutParams(dp(25), -1));
            titleBar.addView(title, new LinearLayout.LayoutParams(dp((getGunButtonWeight(i) - 25)), -1));
            addView(titleBar, new LinearLayout.LayoutParams(-1, dp(44)));
            List<GunButtonLayout> buttons = new ArrayList<>();
            for (int j = 0; j < 5; j++) {
                var shop = new LinearLayout(getContext());
                var gun = new LinearLayout(getContext());
                GunButtonLayout gunButtonLayout = new GunButtonLayout(getContext(), ItemType.values()[i], j);
                buttons.add(gunButtonLayout);
                gun.addView(gunButtonLayout, new LinearLayout.LayoutParams(-1, -1));
                guns.add(gun);

                shop.setGravity(Gravity.CENTER);
                shop.addView(gun, new LinearLayout.LayoutParams(dp(getGunButtonWeight(i)), dp(90)));
                shops.add(shop);

                addView(shop, new LinearLayout.LayoutParams(-1, dp(98)));
            }
            // 添加按钮到全局管理 具体缩放逻辑由窗口直接代理
            shopButtons.put(ItemType.values()[i], buttons);
        }

        public static int getGunButtonWeight(int i){
            return switch (i) {
                case 2 -> 180;
                case 3 -> 200;
                default -> 140;
            };
        }

            private void setScale(float scale) {
                numTab.setTextSize(15 * scale);
                numTab.setPadding((int) (15 * scale), (int) (10 * scale), 0, 0);
                numTab.setLayoutParams(new LinearLayout.LayoutParams(dp(25 * scale), -1));

                title.setLayoutParams(new LinearLayout.LayoutParams(dp((getGunButtonWeight(i) - 25) * scale), -1));
                title.setTextSize(21 * scale);

                titleBar.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(44 * scale)));

                guns.forEach((gun)-> gun.setLayoutParams(new LayoutParams(dp(getGunButtonWeight(i) * scale), dp(90 * scale))));

                shops.forEach((shop)-> shop.setLayoutParams(new LayoutParams(-1, dp(98 * scale))));

                this.setLayoutParams(new LinearLayout.LayoutParams(dp((TypeBarLayout.getGunButtonWeight(i) + 30) * scale), -1));

            }

    }



        public static class GunButtonLayout extends RelativeLayout {
        public static final ColorStateList CT_TINT_LIST = new ColorStateList(
                new int[][]{
                        new int[]{-R.attr.state_enabled},
                        StateSet.get(StateSet.VIEW_STATE_ENABLED)},
                new int[]{
                        DISABLE_TEXTURE_COLOR,
                        RenderUtil.color(150,200,250)
                });

            public static final ColorStateList T_TINT_LIST = new ColorStateList(
                    new int[][]{
                            new int[]{-R.attr.state_enabled},
                            StateSet.get(StateSet.VIEW_STATE_ENABLED)},
                    new int[]{
                            DISABLE_TEXTURE_COLOR,
                            RenderUtil.color(234, 192, 85)
                    });


        public final ItemType type;
        public final int index;
        public final ShapeDrawable backgroud;
        public final RelativeLayout returnGoodsLayout;
        public final ValueAnimator backgroundAnimeFadeIn;
        public final ValueAnimator backgroundAnimeFadeOut;
        public final TextView numText;
        public final TextView itemNameText;
        public final TextView costText;
        public final TextView returnGoodsText;
        public ResourceLocation icon = new ResourceLocation(FPSMatch.MODID, BROKEN_ICON);
        public boolean shouldRenderGunIcon = false;
        public MinecraftSurfaceView minecraftSurfaceView;

        public GunButtonLayout(Context context, ItemType type, int index) {
            super(context);
            this.type = type;
            this.index = index;

            setGravity(Gravity.CENTER);
            setLayoutParams(new LayoutParams(-1, -1));

            this.backgroud = new ShapeDrawable();
            backgroud.setShape(ShapeDrawable.RECTANGLE);
            backgroud.setColor(RenderUtil.color(42, 42, 42));
            backgroud.setCornerRadius(3);
            backgroud.setAlpha(200);
            setBackground(backgroud);

            minecraftSurfaceView = new MinecraftSurfaceView(getContext());
            var msvp = new RelativeLayout.LayoutParams(32, 32);
            msvp.addRule(RelativeLayout.CENTER_IN_PARENT);
            minecraftSurfaceView.setLayoutParams(msvp);
            minecraftSurfaceView.setScaleX(1);
            minecraftSurfaceView.setScaleY(1);
            minecraftSurfaceView.setRenderer(new MinecraftSurfaceView.Renderer() {
                @Override
                public void onSurfaceChanged(int width, int height) {

                }

                @Override
                public void onDraw(@NotNull GuiGraphics gr, int mouseX, int mouseY, float deltaTick, double guiScale, float alpha) {
                    ClientShopSlot currentSlot = ClientData.getSlotData(GunButtonLayout.this.type,GunButtonLayout.this.index);
                    ItemStack itemStack = currentSlot.itemStack();
                    boolean enable = ClientData.getMoney() >= currentSlot.cost() && !itemStack.isEmpty() && !currentSlot.isLocked();
                    /*
                    boolean isGun = itemStack.getItem() instanceof IGun;
                    if(!isGun){
                        boolean enable = ClientData.getMoney() >= currentSlot.cost() && !itemStack.isEmpty() && !currentSlot.isLocked();
                        gr.pose().pushPose();
                        if(GunButtonLayout.this.shouldRenderGunIcon){
                            if(enable){
                                if(ClientData.currentTeam.equals("ct")){
                                    gr.setColor((float) 150 / 255, (float) 200 / 255, (float) 250 / 255,1);
                                }else{
                                    gr.setColor((float) 234 / 255, (float) 192 /255, (float) 85 /255,1);
                                }
                            }else{
                                gr.setColor(65 / 255F,65 / 255F,65 / 255F,1);
                            }
                            gr.blit(GunButtonLayout.this.icon,0,0,0,0,39,13,117,44);
                        }else{
                            if(!enable){
                                gr.setColor(125 / 255F,125 / 255F,125 / 255F,1);
                            }else{
                                gr.setColor(1,1,1,1);
                            }
                            gr.renderItem(itemStack, 0, 0);
                        }

                        gr.pose().popPose();
                    }*/

                    gr.pose().pushPose();
                    if(!enable){
                        gr.setColor(125 / 255F,125 / 255F,125 / 255F,1);
                    }else{
                        gr.setColor(1,1,1,1);
                    }
                    gr.renderItem(itemStack, 0, 0);
                    gr.pose().popPose();
                }
            });
            addView(minecraftSurfaceView);

            numText = new TextView(getContext());
            numText.setTextSize(13);
            numText.setText(String.valueOf(this.index + 1));
            RelativeLayout.LayoutParams numParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            numParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            numParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            numParams.setMargins(5,5,0,0);
            numText.setLayoutParams(numParams);

            itemNameText = new TextView(getContext());
            itemNameText.setTextSize(13);
            itemNameText.setText(I18n.get("fpsm.shop.slot.empty"));
            RelativeLayout.LayoutParams itemNameParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            itemNameParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            itemNameParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            itemNameParams.setMargins(0 , 5, 5,0);
            itemNameText.setLayoutParams(itemNameParams);

            returnGoodsText = new TextView(getContext());
            returnGoodsText.setTextSize(15);
            returnGoodsText.setText("↩");
            RelativeLayout.LayoutParams returnGoodsParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            returnGoodsParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            returnGoodsParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            returnGoodsParams.setMargins(5,12,0,0);
            returnGoodsText.setLayoutParams(returnGoodsParams);
            returnGoodsLayout = new RelativeLayout(getContext()){
                @Override
                public void setEnabled(boolean enabled) {
                    returnGoodsText.setAlpha(enabled ? 255:0);
                    super.setEnabled(enabled);
                }
            };
            returnGoodsLayout.addView(returnGoodsText);
            returnGoodsLayout.setOnClickListener((l)-> FPSMatch.INSTANCE.sendToServer(new ShopActionC2SPacket(ClientData.currentMap,this.type,this.index, ShopAction.RETURN)));

            returnGoodsLayout.setEnabled(false);
            addView(returnGoodsLayout);

            costText = new TextView(getContext());
            costText.setText("$ "+ClientData.getSlotData(this.type, this.index).cost());
            costText.setTextSize(12);
            RelativeLayout.LayoutParams costParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            costParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            costParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            costParams.setMargins(0,0,5,5);
            costText.setLayoutParams(costParams);

            addView(numText);
            addView(itemNameText);
            addView(costText);

            backgroundAnimeFadeIn = ValueAnimator.ofInt(42, 72);
            backgroundAnimeFadeIn.setDuration(200);
            backgroundAnimeFadeIn.setInterpolator(TimeInterpolator.SINE);
            backgroundAnimeFadeIn.addUpdateListener(animation -> {
                int color = (int) animation.getAnimatedValue();
                this.backgroud.setColor(RenderUtil.color(color,color,color));
            });

            backgroundAnimeFadeOut = ValueAnimator.ofInt(72, 42);
            backgroundAnimeFadeOut.setDuration(200);
            backgroundAnimeFadeOut.setInterpolator(TimeInterpolator.SINE);
            backgroundAnimeFadeOut.addUpdateListener(animation -> {
                int color = (int) animation.getAnimatedValue();
                this.backgroud.setColor(RenderUtil.color(color,color,color));
            });

            setOnClickListener((v) -> {
                ClientShopSlot currentSlot = ClientData.getSlotData(this.type,this.index);
                boolean enable = ClientData.getMoney() >= currentSlot.cost() && !currentSlot.itemStack().isEmpty() && !currentSlot.isLocked();
                if(enable) FPSMatch.INSTANCE.sendToServer(new ShopActionC2SPacket(ClientData.currentMap, this.type, this.index, ShopAction.BUY));
            });
        }

        public void setStats(boolean enable){
            backgroud.setStroke(enable ? 1:0,RenderUtil.color(255,255,255));
            this.returnGoodsLayout.setEnabled(enable);
        }

        public void setElements(boolean enable){
            ClientShopSlot currentSlot = ClientData.getSlotData(this.type,this.index);
            if(enable){
                int color = ClientData.currentTeam.equals("ct") ? RenderUtil.color(150,200,250) : RenderUtil.color(234, 192, 85);
                numText.setTextColor(color);
                itemNameText.setTextColor(color);
                costText.setTextColor(color);
            }else{
                numText.setTextColor(CSGameShopScreen.DISABLE_TEXT_COLOR);
                itemNameText.setTextColor(CSGameShopScreen.DISABLE_TEXT_COLOR);
                costText.setTextColor(CSGameShopScreen.DISABLE_TEXT_COLOR);
            }

            if(currentSlot.boughtCount() > 0){
                backgroud.setStroke(1,RenderUtil.color(255,255,255));
            }else{
                backgroud.setStroke(0,RenderUtil.color(255,255,255));
            }

            returnGoodsLayout.setEnabled(currentSlot.canReturn());
        }

        public void updateButtonState() {
            ClientShopSlot currentSlot = ClientData.getSlotData(this.type,this.index);
            boolean enable = ClientData.getMoney() >= currentSlot.cost() && !currentSlot.itemStack().isEmpty() && !currentSlot.isLocked();
            this.setElements(enable);

            if(!this.isHovered()) {
                backgroundAnimeFadeIn.start();
            }else{
                backgroundAnimeFadeOut.start();
            }

            if(refreshFlag){
                ClientShopSlot data = ClientData.getSlotData(this.type,this.index);
                setStats(data.canReturn());
                ItemStack itemStack = data.itemStack();
                boolean empty = itemStack.isEmpty();
                boolean isGun = false;
                if(itemStack.getItem() instanceof IGun igun){
                    isGun = true;
                    TimelessAPI.getClientGunIndex(igun.getGunId(itemStack)).ifPresent(gunIndex -> this.icon = gunIndex.getDefaultDisplay().getHUDTexture());
                }else{
                    this.icon = new ResourceLocation(FPSMatch.MODID, BROKEN_ICON);
                }
                this.itemNameText.setText(empty ? I18n.get("fpsm.shop.slot.empty") : data.name());
                this.costText.setText("$ "+ data.cost());
                this.shouldRenderGunIcon = isGun;
                this.invalidate();

                //END
                if(this.type == ItemType.THROWABLE && this.index == 4){
                    refreshFlag = false;
                }
            }
        }


        private void setScale(float scale) {
            RelativeLayout.LayoutParams numParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            numParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            numParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            numParams.setMargins((int) (5 * scale), (int) (5*scale),0,0);
            numText.setLayoutParams(numParams);
            numText.setTextSize(13*scale);

            RelativeLayout.LayoutParams itemNameParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            itemNameParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            itemNameParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            itemNameParams.setMargins(0 ,(int) (5*scale),(int) (5 * scale),0);
            itemNameText.setLayoutParams(itemNameParams);
            itemNameText.setTextSize(13*scale);

            RelativeLayout.LayoutParams returnGoodsParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            returnGoodsParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            returnGoodsParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            returnGoodsParams.setMargins((int) (5* scale), (int) (12*scale),0,0);
            returnGoodsText.setLayoutParams(returnGoodsParams);
            returnGoodsText.setTextSize(15*scale);

            RelativeLayout.LayoutParams costParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            costParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            costParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            costParams.setMargins(0,0, (int) (5*scale), (int) (5*scale));
            costText.setLayoutParams(costParams);
            costText.setTextSize(12*scale);

            this.setLayoutParams(new LinearLayout.LayoutParams(dp(TypeBarLayout.getGunButtonWeight(this.type.ordinal()) * scale), dp(90 * scale)));
        }
        @Override
        public void draw(@NotNull Canvas canvas) {
            super.draw(canvas);
            updateButtonState();
        }
    }
}
