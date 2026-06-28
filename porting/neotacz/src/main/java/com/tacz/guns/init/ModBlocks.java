package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.block.*;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.block.entity.TargetBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GunMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> TILE_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, GunMod.MOD_ID);

    // 旧方块就让他独占一个了
    public static DeferredBlock<GunSmithTableBlockB> GUN_SMITH_TABLE = BLOCKS.registerBlock("gun_smith_table", GunSmithTableBlockB::new, ModBlocks::tableProperties);
    public static DeferredBlock<GunSmithTableBlockA> WORKBENCH_111 = BLOCKS.registerBlock("workbench_a", GunSmithTableBlockA::new, ModBlocks::tableProperties);
    public static DeferredBlock<GunSmithTableBlockB> WORKBENCH_211 = BLOCKS.registerBlock("workbench_b", GunSmithTableBlockB::new, ModBlocks::tableProperties);
    public static DeferredBlock<GunSmithTableBlockC> WORKBENCH_121 = BLOCKS.registerBlock("workbench_c", GunSmithTableBlockC::new, ModBlocks::tableProperties);

    public static DeferredBlock<TargetBlock> TARGET = BLOCKS.registerBlock("target", TargetBlock::new, ModBlocks::tableProperties);
    public static DeferredBlock<StatueBlock> STATUE = BLOCKS.registerBlock("statue", StatueBlock::new, ModBlocks::statueProperties);

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<GunSmithTableBlockEntity>> GUN_SMITH_TABLE_BE = TILE_ENTITIES.register("gun_smith_table", () -> new BlockEntityType<>(GunSmithTableBlockEntity::new, GUN_SMITH_TABLE.get(), WORKBENCH_111.get(), WORKBENCH_121.get(), WORKBENCH_211.get()));
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<TargetBlockEntity>> TARGET_BE = TILE_ENTITIES.register("target", () -> new BlockEntityType<>(TargetBlockEntity::new, TARGET.get()));
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<StatueBlockEntity>> STATUE_BE = TILE_ENTITIES.register("statue", () -> new BlockEntityType<>(StatueBlockEntity::new, STATUE.get()));
    public static final TagKey<Block> BULLET_IGNORE_BLOCKS = BlockTags.create(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "bullet_ignore"));

    private static BlockBehaviour.Properties tableProperties() {
        return BlockBehaviour.Properties.of().sound(SoundType.WOOD).strength(2.0F, 3.0F).noOcclusion();
    }

    private static BlockBehaviour.Properties statueProperties() {
        return BlockBehaviour.Properties.of().sound(SoundType.STONE).strength(2.0F, 3.0F).noOcclusion();
    }
}
