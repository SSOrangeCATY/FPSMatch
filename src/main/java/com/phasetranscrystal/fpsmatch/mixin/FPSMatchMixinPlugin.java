package com.phasetranscrystal.fpsmatch.mixin;

import com.phasetranscrystal.fpsmatch.compat.impl.FPSMImpl;
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

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean taczTweaksLoaded = FPSMImpl.findTaczTweaks();
        boolean taczLoaded = ModList.get().isLoaded("tacz");

        if (mixinClassName.equals("com.phasetranscrystal.fpsmatch.mixin.ammo.DefaultAmmoMixin")) {
            return !taczTweaksLoaded;
        }
        if (mixinClassName.equals("com.phasetranscrystal.fpsmatch.mixin.ammo.TweakAmmoMixin")) {
            return taczTweaksLoaded;
        }
        if (mixinClassName.contains("compat.spectate.lrt")) {
            return FPSMImpl.findLrtacticalMod();
        }
        if (mixinClassName.contains("compat.spectate.tacz") || mixinClassName.contains("mixin.ammo.")) {
            return taczLoaded;
        }
        if (mixinClassName.contains("collisiobox.MixinRenderHeadShotAABB")) {
            return taczLoaded;
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
