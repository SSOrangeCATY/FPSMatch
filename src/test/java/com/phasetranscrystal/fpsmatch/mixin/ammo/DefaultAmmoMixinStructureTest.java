package com.phasetranscrystal.fpsmatch.mixin.ammo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAmmoMixinStructureTest {

    @Test
    void defaultTaczWallPassIsMarkedAtIgnoredBlockStep() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/mixin/ammo/DefaultAmmoMixin.java"));

        assertTrue(source.contains("@WrapOperation"));
        assertTrue(source.contains("method = \"rayTraceBlocks\""));
        assertTrue(source.contains("BlockRayTrace;performRayTrace"));
        assertTrue(source.contains("hitFunction.apply(rayContext, blockPos)"));
        assertTrue(source.contains("result == null && fpsmatch$isPassThroughWallBlock(level, blockPos)"));
        assertTrue(source.contains("fpsmatch$markPassedWall(rayContext)"));
        assertTrue(source.contains("entity.fpsmatch$setThroughWall(true)"));
    }

    @Test
    void defaultTaczWallPassIgnoresOrdinaryAirMisses() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/mixin/ammo/DefaultAmmoMixin.java"));

        assertTrue(source.contains("private static boolean fpsmatch$isPassThroughWallBlock"));
        assertTrue(source.contains("if (state.isAir()) return false;"));
        assertTrue(source.contains("AmmoConfig.PASS_THROUGH_BLOCKS"));
        assertTrue(source.contains("ModBlocks.BULLET_IGNORE_BLOCKS"));
    }

    @Test
    void defaultTaczWallPassDoesNotDependOnFinalRayTraceReturnValue() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/mixin/ammo/DefaultAmmoMixin.java"));

        assertFalse(source.contains("CallbackInfoReturnable"));
        assertFalse(source.contains("cir.getReturnValue() == null"));
        assertFalse(source.contains("@Inject(method = \"rayTraceBlocks\", at = @At(value = \"RETURN\"))"));
    }

    @Test
    void taczAmmoMixinsOnlyLoadWhenTaczIsPresent() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/phasetranscrystal/fpsmatch/mixin/FPSMatchMixinPlugin.java"));

        assertTrue(source.contains("if (isTaczMixin(mixinClassName))"));
        assertTrue(source.contains("mixinClassName.contains(\"mixin.ammo.\")"));
        assertTrue(source.contains("mixinClassName.endsWith(\"LivingEntityIsDeadOrDyingMixin\")"));
        assertTrue(source.contains("return taczLoaded;"));
    }
}
