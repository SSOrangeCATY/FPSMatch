package com.tacz.guns.block.entity;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.util.GunSmithTableBlockIds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class GunSmithTableBlockEntity extends BlockEntity implements MenuProvider {
    private static final String ID_TAG = "BlockId";

    @Nullable
    private Identifier id = null;

    public GunSmithTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlocks.GUN_SMITH_TABLE_BE.get(), pos, blockState);
    }

    public void setId(Identifier id) {
        this.id = GunSmithTableBlockIds.normalize(id);
    }

    public Identifier getId() {
        return getResolvedId();
    }

    public Identifier getResolvedId() {
        return resolveBlockId(id, getBlockState());
    }

    public static Identifier resolveBlockId(@Nullable Identifier id, BlockState state) {
        Identifier normalizedId = GunSmithTableBlockIds.normalize(id);
        if (normalizedId != null && !DefaultAssets.EMPTY_BLOCK_ID.equals(normalizedId)) {
            return normalizedId;
        }
        if (state.is(ModBlocks.WORKBENCH_111.get())) {
            return ModItems.WORKBENCH_A_ID;
        }
        if (state.is(ModBlocks.WORKBENCH_121.get())) {
            return ModItems.WORKBENCH_C_ID;
        }
        if (state.is(ModBlocks.WORKBENCH_211.get()) || state.is(ModBlocks.GUN_SMITH_TABLE.get())) {
            return DefaultAssets.DEFAULT_BLOCK_ID;
        }
        return DefaultAssets.DEFAULT_BLOCK_ID;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Gun Smith Table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new GunSmithTableMenu(id, inventory, getResolvedId());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.id = input.getString(ID_TAG).map(Identifier::tryParse).map(GunSmithTableBlockIds::normalize).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (id != null) {
            output.putString(ID_TAG, id.toString());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
