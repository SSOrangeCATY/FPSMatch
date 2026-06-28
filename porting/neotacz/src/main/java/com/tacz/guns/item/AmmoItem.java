package com.tacz.guns.item;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.nbt.AmmoItemDataAccessor;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.Map;
import java.util.Optional;

public class AmmoItem extends Item implements AmmoItemDataAccessor {
    public AmmoItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        if (stack.getItem() instanceof IAmmo iAmmo) {
            return TimelessAPI.getCommonAmmoIndex(iAmmo.getAmmoId(stack))
                    .map(CommonAmmoIndex::getStackSize).orElse(1);
        }
        return 1;
    }

    @Override
    @Nonnull
    public Component getName(@Nonnull ItemStack stack) {
        Identifier ammoId = this.getAmmoId(stack);
        Optional<ClientAmmoIndex> ammoIndex = TimelessAPI.getClientAmmoIndex(ammoId);
        if (ammoIndex.isPresent()) {
            return Component.translatable(ammoIndex.get().getName());
        }
        return super.getName(stack);
    }

    private static Comparator<Map.Entry<Identifier, CommonAmmoIndex>> idNameSort() {
        return Comparator.comparingInt(m -> m.getValue().getSort());
    }

    public static NonNullList<ItemStack> fillItemCategory() {
        NonNullList<ItemStack> stacks = NonNullList.create();
        TimelessAPI.getAllCommonAmmoIndex().stream().sorted(idNameSort()).forEach(entry -> {
            ItemStack itemStack = AmmoItemBuilder.create().setId(entry.getKey()).build();
            stacks.add(itemStack);
        });
        return stacks;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag isAdvanced) {
        Identifier ammoId = this.getAmmoId(stack);
        TimelessAPI.getClientAmmoIndex(ammoId).ifPresent(index -> {
            String tooltipKey = index.getTooltipKey();
            if (tooltipKey != null) {
                builder.accept(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
            }
        });

        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(ammoId);
        if (packInfoObject != null) {
            MutableComponent component = Component.translatable(packInfoObject.getName()).withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.ITALIC);
            builder.accept(component);
        }
    }
}
