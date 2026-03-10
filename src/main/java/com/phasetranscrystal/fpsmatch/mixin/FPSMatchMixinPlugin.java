package com.phasetranscrystal.fpsmatch.mixin;

import net.minecraftforge.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Conditional mixin loader for FPSMatch.
 * Only loads compatibility mixins when target mods are present.
 */
public class FPSMatchMixinPlugin implements IMixinConfigPlugin {
    private boolean lrtacticalLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        this.lrtacticalLoaded = ModList.get().isLoaded("lrtactical");
        System.out.println("[FPSMatch] LRTactical loaded: " + lrtacticalLoaded);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Only apply LRTactical-specific mixins when LRTactical is loaded
        if (mixinClassName.contains("compat.spectate.lrt")) {
            return lrtacticalLoaded;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}