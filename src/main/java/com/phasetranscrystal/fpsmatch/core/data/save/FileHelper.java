package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.codec.FPSMCodec;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.ShopMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class FileHelper {
    public static void saveShopData(String levelName ,FPSMShop shop) {
        Path gamePath = FMLLoader.getGamePath();
        File fpsmatchDir = new File(gamePath.toFile(), "fpsmatch");
        if(checkOrCreateFile(fpsmatchDir)){
            File shopDataDir = new File(fpsmatchDir, levelName);
            if(!checkOrCreateFile(shopDataDir)) return;
            File levelData = new File(shopDataDir, "shop");
            if(!checkOrCreateFile(levelData)) return;
            JsonElement json = FPSMCodec.encodeShopDataMapToJson(shop.getDefaultShopData().getData());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonStr = gson.toJson(json);
            File file = new File(levelData, shop.name + ".json");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(jsonStr);
            } catch (IOException e) {
                throw  new RuntimeException(e);
            }
        }
    }

    public static FPSMShop loadShopData(String levelName, BaseMap map) {
        if(map instanceof ShopMap shopMap){
            ShopData defineShopData = shopMap.defineShopData();
            Path gamePath = FMLLoader.getGamePath();
            File fpsmatchDir = new File(gamePath.toFile(), "fpsmatch");
            File levelData = new File(fpsmatchDir, levelName);
            File shopDataDir = new File(levelData, "shop");
            File data = new File(shopDataDir,map.mapName+".json");
            if (checkOrCreateFile(levelData) && shopDataDir.exists() && shopDataDir.isDirectory()) {
                if (data.isFile() && data.getName().endsWith(".json")) {
                    try (FileReader reader = new FileReader(data)) {
                        JsonElement jsonElement = new Gson().fromJson(reader, JsonElement.class);
                        JsonObject jsonObject = jsonElement.getAsJsonObject();
                        Map<ShopData.ItemType, ArrayList<ShopData.ShopSlot>> shopData = FPSMCodec.decodeShopDataMapFromJson(jsonObject);
                        return new FPSMShop(map.mapName, new ShopData(shopData));
                    } catch (IOException e) {
                        throw new RuntimeException("Error reading JSON file: " + data.getName(), e);
                    }
                }else{
                    if(defineShopData != null){
                        return new FPSMShop(map.mapName, defineShopData);
                    }else{
                        return new FPSMShop(map.mapName);
                    }
                }
            }else{
                if(defineShopData != null){
                    return new FPSMShop(map.mapName, defineShopData);
                }else{
                    return new FPSMShop(map.mapName);
                }
            }
        }else{
            throw new RuntimeException("error map is not support shop.");
        }
    }

    public static void saveMaps(String levelName){
        Map<String, List<BaseMap>> maps = FPSMCore.getInstance().getAllMaps();
        Path gamePath = FMLLoader.getGamePath();
        File fpsmatchDir = new File(gamePath.toFile(), "fpsmatch");
        if(checkOrCreateFile(fpsmatchDir)){
            File levelData = new File(fpsmatchDir, levelName);
            if(!checkOrCreateFile(levelData)) return;
            File file = new File(levelData,"spawnpoints.json");
            Map<ResourceLocation, Map<String, List<SpawnPointData>>> data = new HashMap<>();
            maps.forEach((key, mapList) -> {
                mapList.forEach((map)->{
                    Map<String, List<SpawnPointData>> d = map.getMapTeams().getAllSpawnPoints();
                    data.put(new ResourceLocation(key,map.mapName),d);
                });
            });

            JsonElement json = FPSMCodec.encodeMapSpawnPointDataToJson(data);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonStr = gson.toJson(json);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(jsonStr);
            } catch (IOException e) {
                throw  new RuntimeException(e);
            }
        }
    }


    public static Map<ResourceLocation,Map<String,List<SpawnPointData>>> loadMaps(String levelName){
        Map<ResourceLocation,Map<String,List<SpawnPointData>>> data = new HashMap<>();
        Path gamePath = FMLLoader.getGamePath();
        File fpsmatchDir = new File(gamePath.toFile(), "fpsmatch");
        File levelData = new File(fpsmatchDir, levelName);
        File file = new File(levelData,"spawnpoints.json");
        if (checkOrCreateFile(levelData) && file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                JsonElement jsonElement = new Gson().fromJson(reader, JsonElement.class);
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                data = FPSMCodec.decodeMapSpawnPointDataFromJson(jsonObject);
            } catch (IOException e) {
                throw new RuntimeException("Error reading JSON file: " + file.getName(), e);
            }
        }
        return data;
    }



    public static boolean checkOrCreateFile(File file){
        if (!file.exists()) {
           return file.mkdirs();
        }
        return true;
    }
}
