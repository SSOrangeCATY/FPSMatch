package com.phasetranscrystal.fpsmatch.client;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.data.ShopItemData;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.RelativeLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CSGameShopScreen extends Fragment {
    private static final String[] TOP_NAME_KEYS = new String[]{"fpsm.shop.title.equipment","fpsm.shop.title.pistol","fpsm.shop.title.mid_rank","fpsm.shop.title.rifle","fpsm.shop.title.throwable"};
    private static final String[] TOP_NAME_KEYS_TEST = new String[]{"装备","手枪","中级","步枪","投掷物"};
    public static final String TACZ_MODID = "tacz";
    public static final String TACZ_AWP_ICON = "gun/hud/ai_awp.png";
    public static final Map<ShopItemData.ItemType, List<GunButtonLayout>> shopButtons = new HashMap<>();
    public static final int CT_COLOR = RenderUtil.color(150,200,250);
    public static final int T_COLOR = RenderUtil.color(234, 192, 85);
    public static final int DISABLE_TEXTURE_COLOR = RenderUtil.color(65,65,65);
    public static final int DISABLE_TEXT_COLOR = RenderUtil.color(100,100,100);
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        Configurator.setRootLevel(Level.DEBUG);
        try (ModernUI app = new ModernUI()) {
            app.run(new CSGameShopScreen());
        }
        AudioManager.getInstance().close();
        System.gc();
    }
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        var content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.HORIZONTAL);
        var background = new ImageDrawable(Image.create(FPSMatch.MODID,"ui/cs/background.png"));
        var shopWindow = new LinearLayout(this.getContext());
        for(int i = 0; i<5; i++){
            var shopTitleBackground = new ShapeDrawable();
            shopTitleBackground.setShape(ShapeDrawable.RECTANGLE);
            shopTitleBackground.setStroke(0,0xFFFF0000);
            var typeBar = new LinearLayout(getContext());
            typeBar.setOrientation(LinearLayout.VERTICAL);

            var titleBar = new LinearLayout(getContext());

            int gunButtonWeight = switch (i) {
                case 2 -> 180;
                case 3 -> 200;
                default -> 140;
            };
            int textColor = RenderUtil.color(203,203,203);
            TextView numTab = new TextView(getContext());
            numTab.setTextColor(textColor);
            numTab.setText(String.valueOf(i + 1));
            numTab.setTextSize(15);
            numTab.setPadding(15,10,0,0);
            numTab.setGravity(Gravity.LEFT);

            TextView title = new TextView(getContext());
            title.setTextColor(textColor);
            title.setText(I18n.get(TOP_NAME_KEYS_TEST[i]));
            title.setTextSize(21);
            title.setGravity(Gravity.CENTER);

            titleBar.addView(numTab,new LinearLayout.LayoutParams(25, -1));
            titleBar.addView(title,new LinearLayout.LayoutParams(gunButtonWeight - 25, -1));
            typeBar.addView(titleBar,new LinearLayout.LayoutParams(-1, 44));
            List<GunButtonLayout> buttons = new ArrayList<>();
            for(int j = 0; j<5; j++){
                var shopHolderBackground = new ShapeDrawable();
                shopHolderBackground.setShape(ShapeDrawable.RECTANGLE);
                shopHolderBackground.setCornerRadius(3);
                shopHolderBackground.setColor(RenderUtil.color(42,42,42));

                var shop = new LinearLayout(getContext());
                var gun = new LinearLayout(getContext());
                buttons.add( new GunButtonLayout(getContext(), ShopItemData.ItemType.values()[i],j));
                gun.addView(buttons.get(j),new LinearLayout.LayoutParams(-1,-1));
                shop.setGravity(Gravity.CENTER);
                shop.addView(gun,new LinearLayout.LayoutParams(gunButtonWeight,90));
                typeBar.addView(shop,new LinearLayout.LayoutParams(-1,98));
            }
            shopButtons.put(ShopItemData.ItemType.values()[i],buttons);
            shopWindow.addView(typeBar,new LinearLayout.LayoutParams(gunButtonWeight + 30,-1));
        }
        background.setAlpha(100);
        shopWindow.setBackground(background);
        content.addView(shopWindow,new LinearLayout.LayoutParams(950,550));
        return content;
    }



    public static class GunButtonLayout extends RelativeLayout {
        public static final ColorStateList TINT_LIST = new ColorStateList(
                new int[][]{
                        new int[]{-R.attr.state_enabled},
                        StateSet.get(StateSet.VIEW_STATE_ENABLED)},
                new int[]{
                        DISABLE_TEXTURE_COLOR,
                        T_COLOR
                });
        public final ShopItemData.ItemType type;
        public final int index;
        public final ImageView imageView;
        public final ShapeDrawable backgroud;
        public final RelativeLayout returnGoodsLayout;
        public final ValueAnimator backgroundAnimeFadeIn;
        public final ValueAnimator backgroundAnimeFadeOut;

        public final TextView numText;
        public final TextView itemNameText;
        public final TextView costText;

        public final TextView returnGoodsText;

        public GunButtonLayout(Context context, ShopItemData.ItemType type, int index) {
            super(context);
            this.type = type;
            this.index = index;

            setGravity(Gravity.CENTER);
            setLayoutParams(new LayoutParams(-1, -1));

            this.backgroud = new ShapeDrawable();
            backgroud.setShape(ShapeDrawable.RECTANGLE);
            backgroud.setColor(RenderUtil.color(42, 42, 42));
            backgroud.setCornerRadius(3);
            setBackground(backgroud);

            imageView = new ImageView(context);
            ImageDrawable imageDrawable = new ImageDrawable(Image.create(FPSMatch.MODID, TACZ_AWP_ICON));
            var imageParam = new RelativeLayout.LayoutParams(39, 13);
            imageParam.addRule(RelativeLayout.CENTER_IN_PARENT);
            imageView.setLayoutParams(imageParam);
            imageView.setScaleX(3);
            imageView.setScaleY(3);
            imageView.setImageDrawable(imageDrawable);
            imageView.setImageTintList(TINT_LIST);
            addView(imageView);

            numText = new TextView(getContext());
            numText.setTextSize(13);
            numText.setText(String.valueOf(this.index + 1));
            RelativeLayout.LayoutParams numParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            numParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            numParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            numParams.setMargins(5,2,0,0);
            numText.setLayoutParams(numParams);

            itemNameText = new TextView(getContext());
            itemNameText.setTextSize(13);
            itemNameText.setText("AWP");
            RelativeLayout.LayoutParams itemNameParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            itemNameParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            itemNameParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            itemNameParams.setMargins(0,2,5,0);
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
            returnGoodsLayout.setOnClickListener((l)->{
                FPSMShop.getInstance().handleReturnButton(this.type,this.index);
                if(!FPSMShop.getInstance().getSlotData(this.type,this.index).canReturn()){
                    backgroud.setStroke(0,RenderUtil.color(255,255,255));
                    returnGoodsLayout.setEnabled(false);

                    if(this.type == ShopItemData.ItemType.EQUIPMENT){
                        if(this.index == 0){
                            CSGameShopScreen.shopButtons.get(ShopItemData.ItemType.EQUIPMENT).get(1).costText.setText("$"+FPSMShop.getInstance().getSlotData(this.type, 1).cost());
                            CSGameShopScreen.shopButtons.get(ShopItemData.ItemType.EQUIPMENT).get(1).invalidate();
                        }
                    }
                }
            });

            returnGoodsLayout.setEnabled(false);
            addView(returnGoodsLayout);

            costText = new TextView(getContext());
            costText.setText("$"+FPSMShop.getInstance().getSlotData(this.type, this.index).cost());
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
                ShopItemData.ShopSlot currentSlot = FPSMShop.getInstance().getShopItemData().getSlotData(this.type,this.index);
                boolean actionFlag = FPSMShop.getInstance().getMoney() >= currentSlot.cost();
                if(checkSlots(actionFlag)){
                    FPSMShop.getInstance().handleShopButton(this.type,this.index);
                    returnGoodsLayout.setEnabled(true);
                    CSGameShopScreen.shopButtons.get(this.type);
                        if(this.type == ShopItemData.ItemType.RIFLE || this.type == ShopItemData.ItemType.MID_RANK){
                            CSGameShopScreen.shopButtons.get(ShopItemData.ItemType.RIFLE).forEach((bt)->{
                                if(bt.type != this.type){
                                    bt.disableStroke();
                                }else{
                                    if(bt.index != this.index){
                                        bt.disableStroke();
                                    }
                                }
                            });
                            CSGameShopScreen.shopButtons.get(ShopItemData.ItemType.MID_RANK).forEach((bt)->{
                                if(bt.type != this.type){
                                    bt.disableStroke();
                                }else{
                                    if(bt.index != this.index){
                                        bt.disableStroke();
                                    }
                                }
                            });
                        }else if(this.type == ShopItemData.ItemType.PISTOL){
                            CSGameShopScreen.shopButtons.get(ShopItemData.ItemType.PISTOL).forEach((bt)->{
                                if(bt.type != this.type){
                                    bt.disableStroke();
                                }else{
                                    if(bt.index != this.index){
                                        bt.disableStroke();
                                    }
                                }
                            });
                        }else if(this.type == ShopItemData.ItemType.THROWABLE){
                            if(FPSMShop.getInstance().getShopItemData().getThrowableTypeBoughtCount() >= 4){
                                CSGameShopScreen.shopButtons.get(ShopItemData.ItemType.THROWABLE).forEach((bt)->{
                                    bt.setElementsColor(false);
                                });
                            }

                            if(this.index == 0 && currentSlot.boughtCount() >= 2){
                                setElementsColor(false);
                            }
                        }
                    backgroud.setStroke(1,RenderUtil.color(255,255,255));
                }
            });
        }

        public void disableStroke(){
            backgroud.setStroke(0,RenderUtil.color(255,255,255));
            this.returnGoodsLayout.setEnabled(false);
        }

        public void setElementsColor(boolean enable){
            imageView.setEnabled(enable);
            if(enable){
                numText.setTextColor(CSGameShopScreen.T_COLOR);
                itemNameText.setTextColor(CSGameShopScreen.T_COLOR);
                costText.setTextColor(CSGameShopScreen.T_COLOR);
            }else{
                numText.setTextColor(CSGameShopScreen.DISABLE_TEXT_COLOR);
                itemNameText.setTextColor(CSGameShopScreen.DISABLE_TEXT_COLOR);
                costText.setTextColor(CSGameShopScreen.DISABLE_TEXT_COLOR);
            }
        }

        public boolean checkSlots(boolean enable){
            if (!enable) return false;
            if(this.type == ShopItemData.ItemType.THROWABLE){
                if (FPSMShop.getInstance().getShopItemData().getThrowableTypeBoughtCount() >= 4){
                    return false;
                }
                if(this.index == 0){
                    return FPSMShop.getInstance().getShopItemData().getSlotData(this.type, this.index).boughtCount() < 2;
                };
            }

            if(this.type == ShopItemData.ItemType.EQUIPMENT){
                if(this.index == 0){
                    if(FPSMShop.getInstance().getShopItemData().getSlotData(this.type, this.index).canReturn()){
                        CSGameShopScreen.shopButtons.get(ShopItemData.ItemType.EQUIPMENT).get(1).costText.setText("$"+FPSMShop.getInstance().getSlotData(this.type, 1).cost());
                        CSGameShopScreen.shopButtons.get(ShopItemData.ItemType.EQUIPMENT).get(1).invalidate();
                        return false;
                    }
                }
            }
            return !FPSMShop.getInstance().getShopItemData().getSlotData(this.type, this.index).canReturn();
        }

        public void updateButtonState() {
           boolean enable = FPSMShop.getInstance().getMoney() >= FPSMShop.getInstance().getSlotData(this.type,this.index).cost();
           setElementsColor(checkSlots(enable));

           if(!this.isHovered()) {
               backgroundAnimeFadeIn.start();
           }else{
               backgroundAnimeFadeOut.start();
           }

        }
        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            updateButtonState();
        }
    }

}
