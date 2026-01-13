package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber
public class MapCreatorTool extends Item {
    public static final String MODE_TAG = "Mode";
    public static final String DIM_TAG = "Dimension";
    public static final String NEXT_BLOCK_POS_FLAG_TAG = "BlockPosFlag";
    public static final String BLOCK_POS_TAG_1 = "BlockPos1";
    public static final String BLOCK_POS_TAG_2 = "BlockPos2";
    public static final String SPAWN_POS_TAG = "SpawnPos";

    public MapCreatorTool(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        pTooltipComponents.add(Component.literal(""));
        pTooltipComponents.add(Component.translatable("message.fpsm.shop_edit_tool.switch_mode").append(
                Component.translatable("message.fpsm.shop_edit_tool.switch_mode." + (getMode(pStack) ? "region" : "spawn_point"))
        ));
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
    }

    private static boolean switchMode(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        boolean r = !tag.getBoolean(MODE_TAG);
        tag.putBoolean(MODE_TAG, r);
        return r;
    }

    private static boolean getMode(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean(MODE_TAG);
    }

    public static @Nullable MapData getData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (!checkData(tag, false)) return null;
        return new MapData(tag.contains(BLOCK_POS_TAG_2) ? new AABB(BlockPos.of(tag.getLong(BLOCK_POS_TAG_1)), BlockPos.of(tag.getLong(BLOCK_POS_TAG_2))) : new AABB(BlockPos.of(tag.getLong(BLOCK_POS_TAG_1))),
                ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString(DIM_TAG))),
                Arrays.stream(tag.getLongArray(SPAWN_POS_TAG)).mapToObj(BlockPos::of).toList());
    }

    public static boolean checkData(CompoundTag tag, boolean checkSpawnPos) {
        return tag != null &&
                tag.get(BLOCK_POS_TAG_1) instanceof LongTag &&
                tag.get(DIM_TAG) instanceof StringTag strTag && !strTag.getAsString().isEmpty() &&
                (!checkSpawnPos || tag.get(SPAWN_POS_TAG) instanceof LongArrayTag longListTag && !(longListTag.getAsLongArray().length == 0));
    }

    @Override

    public InteractionResult useOn(UseOnContext pContext) {
        CompoundTag tag = pContext.getItemInHand().getOrCreateTag();
        if (getMode(pContext.getItemInHand())) {//区域选择模式
            boolean flag = tag.getBoolean(NEXT_BLOCK_POS_FLAG_TAG);
            String dimName = pContext.getLevel().dimension().location().toString();
            boolean flag2 = tag.getString(DIM_TAG).equals(dimName);
            if (flag2) {
                tag.putString(DIM_TAG, dimName);
            }
            if (!flag || flag2) {//如果标志1为否或维度信息不符，写入pos1
                tag.putLong(BLOCK_POS_TAG_1, pContext.getClickedPos().asLong());
                tag.putBoolean(NEXT_BLOCK_POS_FLAG_TAG, true);
            } else {
                tag.putLong(BLOCK_POS_TAG_2, pContext.getClickedPos().asLong());
                tag.putBoolean(NEXT_BLOCK_POS_FLAG_TAG, false);
            }
        }
        return super.useOn(pContext);
    }

    //当使用物品右键空气时，切换状态
    @SubscribeEvent
    public static void itemInteractAir(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getItemStack().is(FPSMItemRegister.MAP_CREATOR_TOOL.get()) && event.getEntity().isShiftKeyDown()) {
            event.getEntity().displayClientMessage(Component.translatable("message.fpsm.shop_edit_tool.switch_mode").append(
                    Component.translatable("message.fpsm.shop_edit_tool.switch_mode." + (switchMode(event.getItemStack()) ? "region" : "spawn_point"))
            ), true);
        }
    }

    public record MapData(AABB region, ResourceKey<Level> dim, List<BlockPos> spawnPoints) {
    }

}
