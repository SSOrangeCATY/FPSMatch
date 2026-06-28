package com.tacz.guns.block.entity;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModSounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import static com.tacz.guns.block.TargetBlock.OUTPUT_POWER;
import static com.tacz.guns.block.TargetBlock.STAND;

public class TargetBlockEntity extends BlockEntity implements Nameable {
    /**
     * 标靶复位时间，暂定为 5 秒
     */
    private static final int RESET_TIME = 5 * 20;
    private static final String OWNER_TAG = "Owner";
    private static final String OWNER_NAME_TAG = "Name";
    private static final String OWNER_ID_TAG = "Id";
    private static final String CUSTOM_NAME_TAG = "CustomName";
    public float rot = 0;
    public float oRot = 0;
    private @Nullable GameProfile owner;
    private @Nullable Component name;

    public TargetBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlocks.TARGET_BE.get(), pos, blockState);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TargetBlockEntity pBlockEntity) {
        pBlockEntity.oRot = pBlockEntity.rot;
        if (state.getValue(STAND)) {
            pBlockEntity.rot = Math.max(pBlockEntity.rot - 18, 0);
        } else {
            pBlockEntity.rot = Math.min(pBlockEntity.rot + 45, 90);
        }
    }

    @Nullable
    public GameProfile getOwner() {
        return owner;
    }

    public void setOwner(@Nullable GameProfile owner) {
        this.owner = normalizeOwner(owner);
        this.refresh();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read(OWNER_TAG, CompoundTag.CODEC).map(TargetBlockEntity::readOwner).orElse(null);
        this.name = BlockEntity.parseCustomNameSafe(input, CUSTOM_NAME_TAG);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) {
            output.store(OWNER_TAG, CompoundTag.CODEC, writeOwner(owner));
        }
        if (this.name != null) {
            output.store(CUSTOM_NAME_TAG, ComponentSerialization.CODEC, this.name);
        }
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : Component.empty();
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    public void setCustomName(Component name) {
        this.name = name;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    public void refresh() {
        this.setChanged();
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    public void hit(Level level, BlockState state, BlockHitResult hit, boolean isUpperBlock) {
        if (this.level != null && state.getValue(STAND)) {
            BlockPos blockPos = hit.getBlockPos();
            // 如果是击中上方，把状态移动到下方处理
            if (isUpperBlock) {
                blockPos = blockPos.below();
                state = level.getBlockState(blockPos);
            }
            int redstoneStrength = TargetBlock.getRedstoneStrength(hit, isUpperBlock);
            level.setBlock(blockPos, state.setValue(STAND, false).setValue(OUTPUT_POWER, redstoneStrength), Block.UPDATE_ALL);
            level.scheduleTick(blockPos, state.getBlock(), RESET_TIME);
            // 原版的声音传播距离由 volume 决定
            // 当声音大于 1 时，距离为 = 16 * volume
            float volume = OtherConfig.TARGET_SOUND_DISTANCE.get() / 16.0f;
            volume = Math.max(volume, 0);
            level.playSound(null, blockPos, ModSounds.TARGET_HIT.get(), SoundSource.BLOCKS, volume, level.getRandom().nextFloat() * 0.1F + 0.9F);
        }
    }

    @Nullable
    private static GameProfile normalizeOwner(@Nullable GameProfile owner) {
        if (owner == null) {
            return null;
        }
        UUID id = owner.id() == null ? Util.NIL_UUID : owner.id();
        String name = owner.name() == null ? "" : owner.name();
        return new GameProfile(id, name, owner.properties());
    }

    @Nullable
    private static GameProfile readOwner(CompoundTag tag) {
        String name = tag.getString(OWNER_NAME_TAG).or(() -> tag.getString("name")).orElse("");
        Optional<UUID> id = tag.getString(OWNER_ID_TAG).or(() -> tag.getString("id")).flatMap(TargetBlockEntity::tryParseUuid);
        if (name.isBlank() && id.isEmpty()) {
            return null;
        }
        return new GameProfile(id.orElse(Util.NIL_UUID), name);
    }

    private static CompoundTag writeOwner(GameProfile owner) {
        GameProfile normalized = normalizeOwner(owner);
        CompoundTag tag = new CompoundTag();
        if (normalized == null) {
            return tag;
        }
        if (!Util.NIL_UUID.equals(normalized.id())) {
            tag.putString(OWNER_ID_TAG, normalized.id().toString());
        }
        if (normalized.name() != null && !normalized.name().isBlank()) {
            tag.putString(OWNER_NAME_TAG, normalized.name());
        }
        return tag;
    }

    private static Optional<UUID> tryParseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
