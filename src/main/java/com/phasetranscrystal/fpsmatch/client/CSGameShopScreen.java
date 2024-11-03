package com.phasetranscrystal.fpsmatch.client;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.ShopItemData;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import icyllis.modernui.ModernUI;
import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;


public class CSGameShopScreen extends Fragment {
    private static final String[] TOP_NAME_KEYS = new String[]{"fpsm.shop.title.equipment","fpsm.shop.title.pistol","fpsm.shop.title.mid_rank","fpsm.shop.title.rifle","fpsm.shop.title.throwable"};
    private static final String[] TOP_NAME_KEYS_TEST = new String[]{"装备","手枪","中级","步枪","投掷物"};
    private final ShopItemData data = new ShopItemData();
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        Configurator.setRootLevel(Level.DEBUG);
        try (ModernUI app = new ModernUI()) {
            app.run(new CSGameShopScreen()); // 在这里传入您的 Fragment 实例
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

            for(int j = 0; j<5; j++){
                var shopHolderBackground = new ShapeDrawable();
                shopHolderBackground.setShape(ShapeDrawable.RECTANGLE);
                shopHolderBackground.setCornerRadius(3);
                shopHolderBackground.setColor(RenderUtil.color(42,42,42));

                var shop = new LinearLayout(getContext());
                var gun = new LinearLayout(getContext());
                var gunButton = new GunButtonView(getContext(),this.data.getSlotData(ShopItemData.ItemType.values()[i],j));
                gunButton.setBackground(shopHolderBackground);
                gun.addView(gunButton,new LinearLayout.LayoutParams(-1,-1));
                shop.setGravity(Gravity.CENTER);
                shop.addView(gun,new LinearLayout.LayoutParams(gunButtonWeight,90));
                typeBar.addView(shop,new LinearLayout.LayoutParams(-1,98));
            }
            shopWindow.addView(typeBar,new LinearLayout.LayoutParams(gunButtonWeight + 30,-1));
        }
        background.setAlpha(100);
        shopWindow.setBackground(background);
        content.addView(shopWindow,new LinearLayout.LayoutParams(950,550));
        return content;
    }



    public static class GunButtonView extends View {
        public final ShopItemData.ShopSlot shopSlot;
        public boolean isBuy = false;
        public GunButtonView(Context context, ShopItemData.ShopSlot shopSlot) {
            super(context);
            this.shopSlot = shopSlot;
            this.setOnClickListener((l)->{
                    if(!this.isBuy){
                        this.isBuy = true;
                        ItemStack itemStack = this.shopSlot.item().itemStack();
                        System.out.println("bought : " + (itemStack == null ? "debugItem" : itemStack.getDisplayName().getString())+ " cost->" + this.shopSlot.item().cost());
                        ((ShapeDrawable) this.getBackground()).setStroke(1, RenderUtil.color(255,255,255));
                    }else{
                        this.isBuy = false;
                        ItemStack itemStack = this.shopSlot.item().itemStack();
                        System.out.println("return goods : " + (itemStack == null ? "debugItem" : itemStack.getDisplayName().getString())+ " return cost->" + this.shopSlot.item().cost());
                        ((ShapeDrawable) this.getBackground()).setStroke(0, RenderUtil.color(255,255,255));
                    }

            });

        }
    }

}
