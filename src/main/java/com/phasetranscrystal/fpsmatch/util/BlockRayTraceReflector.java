package com.phasetranscrystal.fpsmatch.util;

import com.tacz.guns.init.ModBlocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class BlockRayTraceReflector {
    public static final Predicate<BlockState> IGNORES = input -> input != null &&
            input.is(ModBlocks.BULLET_IGNORE_BLOCKS);

    private static final Method PERFORM_RAY_TRACE_METHOD;
    private static final Method GET_BLOCK_HIT_RESULT_METHOD;
    
    static {
        try {
            Class<?> blockRayTraceClass = Class.forName("com.tacz.guns.util.block.BlockRayTrace");
            
            // 获取私有方法
            PERFORM_RAY_TRACE_METHOD = blockRayTraceClass.getDeclaredMethod(
                "performRayTrace",
                ClipContext.class,
                BiFunction.class,
                Function.class
            );
            PERFORM_RAY_TRACE_METHOD.setAccessible(true);
            
            GET_BLOCK_HIT_RESULT_METHOD = blockRayTraceClass.getDeclaredMethod(
                "getBlockHitResult",
                Level.class,
                ClipContext.class,
                BlockPos.class,
                BlockState.class
            );
            GET_BLOCK_HIT_RESULT_METHOD.setAccessible(true);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize BlockRayTrace reflector", e);
        }
    }
    
    /**
     * 调用私有方法 performRayTrace
     */
    @SuppressWarnings("unchecked")
    public static <T> T performRayTrace(
        ClipContext context,
        BiFunction<ClipContext, BlockPos, T> hitFunction,
        Function<ClipContext, T> missFactory
    ) {
        try {
            return (T) PERFORM_RAY_TRACE_METHOD.invoke(null, context, hitFunction, missFactory);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke performRayTrace", e);
        }
    }
    
    /**
     * 调用私有方法 getBlockHitResult
     */
    @Nullable
    public static BlockHitResult getBlockHitResult(
        Level level,
        ClipContext context,
        BlockPos pos,
        BlockState blockState
    ) {
        try {
            return (BlockHitResult) GET_BLOCK_HIT_RESULT_METHOD.invoke(
                null, level, context, pos, blockState
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke getBlockHitResult", e);
        }
    }
}