package com.phasetranscrystal.fpsmatch.datagen;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class FPSMDataGenerators {
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
    }
}
