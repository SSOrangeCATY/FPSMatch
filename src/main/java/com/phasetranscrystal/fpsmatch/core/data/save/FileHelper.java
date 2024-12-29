package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.codec.FPSMCodec;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BlastModeMap;
import com.phasetranscrystal.fpsmatch.core.map.GiveStartKitsMap;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ChangeShopItemModule;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class FileHelper {
    public static final File FPS_MATCH_DIR = new File(FMLLoader.getGamePath().toFile(), "fpsmatch");

    public static void saveChangeItemListenerModule(ListenerModule module){
        if(module instanceof ChangeShopItemModule cSIM){
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            File dir = new File(FPS_MATCH_DIR, "ChangeShopItemModule");
            if(checkOrCreateFile(dir)){
                File file = new File(dir, module.getName()+".json");
                    try (FileWriter writer = new FileWriter(file)) {
                        JsonElement json = ChangeShopItemModule.encodeToJson(cSIM);
                        String str = gson.toJson(json);
                        writer.write(str);
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }
            }
        }
    }

    public static List<ChangeShopItemModule> loadListenerModules(){
        List<ChangeShopItemModule> listenerModules = new ArrayList<>();
        File dir = new File(FPS_MATCH_DIR, "ChangeShopItemModule");
        if(checkOrCreateFile(dir)){
            for (File file : Objects.requireNonNull(dir.listFiles())) {
                if (file.isFile()) {
                    try (FileReader reader = new FileReader(file)) {
                        JsonElement jsonElement = new Gson().fromJson(reader, JsonElement.class);
                        listenerModules.add(ChangeShopItemModule.decodeFromJson(jsonElement));
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return listenerModules;
    }


    public static void saveMaps(String levelName){
        Map<String, List<BaseMap>> maps = FPSMCore.getInstance().getAllMaps();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if(checkOrCreateFile(FPS_MATCH_DIR)) {
            File worldsDir = new File(FPS_MATCH_DIR, "world");
            File dataDir = new File(worldsDir, levelName);
            if(!checkOrCreateFile(dataDir)) return;
            if (dataDir.exists() && dataDir.isDirectory()) {
                for (List<BaseMap> baseMapList : maps.values()){
                    for (BaseMap map : baseMapList){
                        File mapDir = new File(dataDir,map.gameType+"_"+map.mapName);
                        if(!checkOrCreateFile(mapDir)) return;

                        JsonElement dj = FPSMCodec.encodeLevelResourceKeyToJson(map.getServerLevel().dimension());
                        String dStr = gson.toJson(dj);
                        File dimensionFile = new File(mapDir, "dimension.json");
                        try (FileWriter writer = new FileWriter(dimensionFile)) {
                            writer.write(dStr);
                        } catch (IOException e) {
                            throw  new RuntimeException(e);
                        }

                        JsonElement aj = FPSMCodec.encodeAreaDataToJson(map.getMapArea());
                        String aStr = gson.toJson(aj);
                        File aFile = new File(mapDir, "area.json");
                        try (FileWriter writer = new FileWriter(aFile)) {
                            writer.write(aStr);
                        } catch (IOException e) {
                            throw  new RuntimeException(e);
                        }

                        if(map instanceof ShopMap shopMap){
                            JsonElement json = FPSMCodec.encodeShopDataMapToJson(shopMap.getShop().getDefaultShopData().getData());
                            String jsonStr = gson.toJson(json);
                            File file = new File(mapDir, "shop.json");
                            try (FileWriter writer = new FileWriter(file)) {
                                writer.write(jsonStr);
                            } catch (IOException e) {
                                throw  new RuntimeException(e);
                            }
                        }

                        if (map instanceof BlastModeMap<?> blastModeMap){
                            JsonElement json = FPSMCodec.encodeAreaDataListToJson(blastModeMap.getBombAreaData());
                            String jsonStr = gson.toJson(json);
                            File file = new File(mapDir,"blast.json");
                            try (FileWriter writer = new FileWriter(file)) {
                                writer.write(jsonStr);
                            } catch (IOException e) {
                                throw  new RuntimeException(e);
                            }
                        }

                        if(map instanceof GiveStartKitsMap<?> giveStartKitsMap){
                            Map<String,List<ItemStack>> teamKits = new HashMap<>();
                            for(BaseTeam team : map.getMapTeams().getTeams()){
                                teamKits.put(team.getFixedName(),giveStartKitsMap.getKits(team));
                            }
                            JsonElement json = FPSMCodec.encodeTeamKitsToJson(teamKits);
                            String jsonStr = gson.toJson(json);
                            File file = new File(mapDir,"startKit.json");
                            try (FileWriter writer = new FileWriter(file)) {
                                writer.write(jsonStr);
                            } catch (IOException e) {
                                throw  new RuntimeException(e);
                            }
                        }

                        File mapData = new File(mapDir,"teams.json");
                        Map<String, List<SpawnPointData>> d = map.getMapTeams().getAllSpawnPoints();
                        JsonElement json = FPSMCodec.encodeMapSpawnPointDataToJson(d);
                        String jsonStr = gson.toJson(json);
                        try (FileWriter writer = new FileWriter(mapData)) {
                            writer.write(jsonStr);
                        } catch (IOException e) {
                            throw  new RuntimeException(e);
                        }
                    }
                }
            }
        }
    }

    public static List<RawMapData> loadMaps(String levelName) {
        List<RawMapData> loadedMaps = new ArrayList<>();
        File worldsDir = new File(FPS_MATCH_DIR, "world");
        File dataDir = new File(worldsDir, levelName);
        if(!checkOrCreateFile(dataDir)) return loadedMaps;
        if (dataDir.exists() && dataDir.isDirectory()) {
            for (File mapDir : Objects.requireNonNull(dataDir.listFiles())) {
                if (mapDir.isDirectory()) {
                    String gameType = mapDir.getName().split("_")[0];
                    String mapName = mapDir.getName().split("_")[1];
                    ResourceLocation mapRL = new ResourceLocation(gameType, mapName);

                    List<AreaData> blastAreaDataList;
                    Map<ItemType, ArrayList<ShopSlot>> shop;
                    ResourceKey<Level> levelResourceKey = null;
                    AreaData areaData = null;
                    // Load teams.json
                    File mapData = new File(mapDir, "teams.json");
                    if (mapData.exists() && mapData.isFile()) {
                        try (FileReader reader = new FileReader(mapData)) {
                            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                            Map<String, List<SpawnPointData>> teamsData = FPSMCodec.decodeMapSpawnPointDataFromJson(json);
                            File dF = new File(mapDir, "dimension.json");
                            if (dF.exists() && dF.isFile()) {
                                try (FileReader fileReader = new FileReader(dF)) {
                                    JsonElement jsonElement = new Gson().fromJson(fileReader, JsonElement.class);
                                    levelResourceKey = FPSMCodec.decodeLevelResourceKeyFromJson(jsonElement);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            File aF = new File(mapDir, "area.json");
                            if (aF.exists() && aF.isFile()) {
                                try (FileReader fileReader = new FileReader(aF)) {
                                    JsonElement jsonElement = new Gson().fromJson(fileReader, JsonElement.class);
                                    areaData = FPSMCodec.decodeAreaDataFromJson(jsonElement);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            if(levelResourceKey != null && areaData!= null){
                                RawMapData rawMapData = new RawMapData(mapRL, teamsData,levelResourceKey,areaData);
                                File shopFile = new File(mapDir, "shop.json");
                                if (shopFile.exists() && shopFile.isFile()) {
                                    try (FileReader shopReader = new FileReader(shopFile)) {
                                        JsonElement shopJson = new Gson().fromJson(shopReader, JsonElement.class);
                                        shop = FPSMCodec.decodeShopDataMapFromJson(shopJson);
                                        rawMapData.setShop(shop);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                File blastFile = new File(mapDir, "blast.json");
                                if (blastFile.exists() && blastFile.isFile()) {
                                    try (FileReader blastReader = new FileReader(blastFile)) {
                                        JsonElement blastJson = new Gson().fromJson(blastReader, JsonElement.class);
                                        blastAreaDataList = FPSMCodec.decodeAreaDataListFromJson(blastJson);
                                        rawMapData.setBlastAreaDataList(blastAreaDataList);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                File startKitFile = new File(mapDir, "startKit.json");
                                if (startKitFile.exists() && startKitFile.isFile()) {
                                    try (FileReader startKitReader = new FileReader(startKitFile)) {
                                        JsonElement startKitJson = new Gson().fromJson(startKitReader, JsonElement.class);
                                        Map<String,List<ItemStack>> rawData = FPSMCodec.decodeTeamKitsFromJson(startKitJson);
                                        Map<String, ArrayList<ItemStack>> startKitList = new HashMap<>();
                                        for (String teamName : rawData.keySet()){
                                            ArrayList<ItemStack> itemStacks = new ArrayList<>();
                                            for (ItemStack itemStack : rawData.get(teamName)){
                                                ItemStack stack = itemStack.copy();
                                                itemStacks.add(stack);
                                            }
                                            startKitList.put(teamName,itemStacks);
                                        }
                                        rawMapData.setStartKits(startKitList);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                loadedMaps.add(rawMapData);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        return loadedMaps;
    }


    public static boolean checkOrCreateFile(File file){
        if (!file.exists()) {
           return file.mkdirs();
        }
        return true;
    }

    public static class RawMapData{
        @NotNull public final ResourceLocation mapRL;
        @NotNull public final ResourceKey<Level> levelResourceKey;
        @NotNull public final AreaData areaData;
        @NotNull public final Map<String,List<SpawnPointData>> teamsData;
        @Nullable public Map<ItemType, ArrayList<ShopSlot>> shop;
        @Nullable public List<AreaData> blastAreaDataList;
        @Nullable public Map<String,ArrayList<ItemStack>> startKits;

        public RawMapData(@NotNull ResourceLocation mapRL, @NotNull Map<String, List<SpawnPointData>> teamsData, @NotNull ResourceKey<Level> levelResourceKey, @NotNull AreaData areaData) {
            this.mapRL = mapRL;
            this.teamsData = teamsData;
            this.levelResourceKey = levelResourceKey;
            this.areaData = areaData;
        }

        public void setStartKits(@Nullable Map<String,ArrayList<ItemStack>> startKits) {
            this.startKits = startKits;
        }

        public void setBlastAreaDataList(@Nullable List<AreaData> blastAreaDataList) {
            this.blastAreaDataList = blastAreaDataList;
        }

        public void setShop(@Nullable Map<ItemType, ArrayList<ShopSlot>> shop) {
            this.shop = shop;
        }

    }
}
