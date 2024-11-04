package com.phasetranscrystal.fpsmatch.client;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.data.ShopItemData;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
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
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class CSGameShopScreen extends Fragment {
    private static final String[] TOP_NAME_KEYS = new String[]{"fpsm.shop.title.equipment","fpsm.shop.title.pistol","fpsm.shop.title.mid_rank","fpsm.shop.title.rifle","fpsm.shop.title.throwable"};
    private static final String[] TOP_NAME_KEYS_TEST = new String[]{"装备","手枪","中级","步枪","投掷物"};
    public static final String TACZ_MODID = "tacz";
    public static final String TACZ_AWP_ICON = "gun/hud/ai_awp.png";
    public static final Map<ShopItemData.ItemType, List<GunButtonLinearLayout>> shopButtons = new HashMap<>();
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
            List<GunButtonLinearLayout> buttons = new ArrayList<>();
            for(int j = 0; j<5; j++){
                var shopHolderBackground = new ShapeDrawable();
                shopHolderBackground.setShape(ShapeDrawable.RECTANGLE);
                shopHolderBackground.setCornerRadius(3);
                shopHolderBackground.setColor(RenderUtil.color(42,42,42));

                var shop = new LinearLayout(getContext());
                var gun = new LinearLayout(getContext());
                buttons.add( new GunButtonLinearLayout(getContext(), ShopItemData.ItemType.values()[i],j));
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



    public static class GunButtonLinearLayout extends LinearLayout {
        public final ShopItemData.ItemType type;
        public final int index;
        public final ImageView imageView;
        public final ShapeDrawable buttonBackground;

        public GunButtonLinearLayout(Context context, ShopItemData.ItemType type, int index) {
            super(context);
            this.type = type;
            this.index = index;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
            this.buttonBackground = new ShapeDrawable();
            buttonBackground.setShape(ShapeDrawable.RECTANGLE);
            buttonBackground.setColor(RenderUtil.color(42, 42, 42));
            buttonBackground.setCornerRadius(3);
            setBackground(buttonBackground);
            imageView = new ImageView(context);
            ImageDrawable imageDrawable = new ImageDrawable(Image.create(FPSMatch.MODID, TACZ_AWP_ICON));
            imageView.setLayoutParams(new LinearLayout.LayoutParams(39, 13));
            imageView.setScaleX(3);
            imageView.setScaleY(3);
            imageView.setImageDrawable(imageDrawable);
            ColorStateList tintList = new ColorStateList(
                    new int[][] {
                            new int[]{-R.attr.state_enabled},
                            StateSet.get(StateSet.VIEW_STATE_ENABLED)},
                    new int[] {
                            RenderUtil.color(65,65,65),
                            RenderUtil.color(234,192,85)
                    });
            imageView.setImageTintList(tintList);
            addView(imageView);
            setOnClickListener((v) -> {
                boolean actionFlag = FPSMShop.getInstance().getButtonsData().get(this.type).get(this.index);
                boolean flag;
                if(actionFlag){
                    flag = FPSMShop.getInstance().handleShopButton(this.type,this.index);
                    if(flag) buttonBackground.setStroke(1,RenderUtil.color(255,255,255));
                }else{
                    flag = FPSMShop.getInstance().handleReturnButton(this.type,this.index);
                }
                this.setEnabled(flag);
            });
        }
        public void updateButtonState() {
           boolean enable = FPSMShop.getInstance().getButtonsData().get(this.type).get(this.index);
           this.setEnabled(enable);
        }
        @Override
        public void setEnabled(boolean enabled) {
            imageView.setEnabled(enabled);
        }
        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            updateButtonState();
        }
    }

}
