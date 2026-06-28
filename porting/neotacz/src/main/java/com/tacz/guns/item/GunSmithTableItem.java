package com.tacz.guns.item;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.util.GunSmithTableBlockIds;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Optional;

public class GunSmithTableItem extends BlockItem implements BlockItemDataAccessor {
    private final Identifier defaultBlockId;

    public GunSmithTableItem(Block block, Item.Properties properties) {
        this(block, properties, DefaultAssets.EMPTY_BLOCK_ID);
    }

    public GunSmithTableItem(Block block, Item.Properties properties, Identifier defaultBlockId) {
        super(block, properties.stacksTo(1).useBlockDescriptionPrefix());
        this.defaultBlockId = defaultBlockId;
    }

    public static NonNullList<ItemStack> fillItemCategory() {
        NonNullList<ItemStack> stacks = NonNullList.create();
        TimelessAPI.getAllCommonBlockIndex().forEach((blockIndex) -> {
            ItemStack stack = BlockItemBuilder.create(blockIndex.getValue().getBlock()).setId(blockIndex.getKey()).build();
            stacks.add(stack);
        });
        return stacks;
    }

    @Override
    @Nonnull
    public Identifier getBlockId(ItemStack block) {
        Identifier blockId = BlockItemDataAccessor.super.getBlockId(block);
        Identifier resolvedId = DefaultAssets.EMPTY_BLOCK_ID.equals(blockId) ? defaultBlockId : blockId;
        return GunSmithTableBlockIds.normalize(resolvedId);
    }

    @Override
    @Nonnull
    public Component getName(@Nonnull ItemStack stack) {
        Identifier blockId = this.getBlockId(stack);
        Optional<ClientBlockIndex> blockIndex = TimelessAPI.getClientBlockIndex(blockId);
        if (blockIndex.isPresent()) {
            return Component.translatable(blockIndex.get().getName());
        }
        return super.getName(stack);
    }

//    @Override
//    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag isAdvanced) {
//        Identifier blockId = this.getBlockId(stack);
//        TimelessAPI.getClientBlockIndex(blockId).ifPresent(index -> {
//            String tooltipKey = index.getTooltipKey();
//            if (tooltipKey != null) {
//                components.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
//            }
//        });
//
//        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(blockId);
//        if (packInfoObject != null) {
//            MutableComponent component = Component.translatable(packInfoObject.getName()).withStyle(ChatFormatting.BLUE).withStyle(ChatFormatting.ITALIC);
//            components.add(component);
//        }
//    }

    @Override
    @NotNull
    public Optional<TooltipComponent> getTooltipImage(ItemStack pStack) {
        return Optional.empty();
    }
}
