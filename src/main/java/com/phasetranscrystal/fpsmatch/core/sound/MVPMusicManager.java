package com.phasetranscrystal.fpsmatch.core.sound;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.data.save.ISavedData;
import com.phasetranscrystal.fpsmatch.core.data.save.SaveHolder;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MVPMusicManager implements ISavedData<MVPMusicManager> {
    public static final Codec<MVPMusicManager> CODEC = Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).xmap(MVPMusicManager::new,
            (manager)-> manager.mvpMusicMap);
    private static MVPMusicManager INSTANCE;
    private final Map<String, ResourceLocation> mvpMusicMap;

    public static MVPMusicManager getInstance() {
        if(INSTANCE == null){
            INSTANCE = new MVPMusicManager();
        }
        return INSTANCE;
    }

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
                new SaveHolder<>(
                        MVPMusicManager.CODEC,
                        MVPMusicManager::read,
                        MVPMusicManager::write,
                        MVPMusicManager::merge,
                        true
                )
        );
    }

    private void read() {
        INSTANCE = this;
    }

    @Override
    public Codec<MVPMusicManager> codec() {
        return CODEC;
    }

    public static void write(FPSMDataManager manager){
        manager.saveData(MVPMusicManager.getInstance(),"data");
    }

    public static MVPMusicManager merge(MVPMusicManager old, MVPMusicManager newer){
        old.mvpMusicMap.putAll(newer.mvpMusicMap);
        return old;
    }
}
