package com.phasetranscrystal.fpsmatch.core.sound;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.data.save.SaveHolder;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MVPMusicManager{
    public static final Codec<MVPMusicManager> CODEC = Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).xmap(MVPMusicManager::new,
            (manager)-> manager.mvpMusicMap);
    private final Map<String, ResourceLocation> mvpMusicMap;

    public MVPMusicManager(){
        mvpMusicMap = Maps.newHashMap();
    }

    public MVPMusicManager(Map<String, ResourceLocation> mvpMusicMap){
        this.mvpMusicMap = Maps.newHashMap();
        this.mvpMusicMap.putAll(mvpMusicMap);
    }

    public void addMvpMusic(String uuid, ResourceLocation music){
        mvpMusicMap.put(uuid, music);
    }

    public ResourceLocation getMvpMusic(String uuid){
        return this.mvpMusicMap.getOrDefault(uuid, new ResourceLocation("fpsmatch:empty"));
    }

    public boolean playerHasMvpMusic(String uuid){
        return this.mvpMusicMap.containsKey(uuid);
    }

    @SubscribeEvent
    public static void onDataRegister(RegisterFPSMSaveDataEvent event){
        event.registerData(MVPMusicManager.class,"MvpMusicData",
                new SaveHolder.Builder<>(CODEC)
                        .withReadHandler(MVPMusicManager::read)
                        .withWriteHandler(MVPMusicManager::write)
                        .withMergeHandler(MVPMusicManager::merge)
                        .isGlobal(true)
                        .build()
        );
    }

    private void read() {
        FPSMCore.getInstance().getMvpMusicManager().read(this);
    }

    private void read(MVPMusicManager mvpMusicManager) {
        this.mvpMusicMap.putAll(mvpMusicManager.mvpMusicMap);
    }

    public static void write(FPSMDataManager manager){
        manager.saveData(FPSMCore.getInstance().getMvpMusicManager(),"data");
    }

    public static MVPMusicManager merge(@Nullable MVPMusicManager old, MVPMusicManager newer){
        if(old == null){
            return newer;
        }else{
            old.read(newer);
            return old;
        }
    }
}
