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
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class FileHelper {
    public static void saveShopData() {
        Map<String, FPSMShop> data = FPSMShop.getAllShopData();
        Path gamePath = FMLLoader.getGamePath();
        File fpsmatchDir = new File(gamePath.toFile(), "fpsmatch");
        if(checkOrCreateFile(fpsmatchDir)){
            File shopDataDir = new File(fpsmatchDir, "shop");
            if(!checkOrCreateFile(shopDataDir)) return;
            data.forEach((key, shop) -> {
                JsonElement json = FPSMCodec.encodeShopDataMapToJson(shop.getDefaultShopData().getData());
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String jsonStr = gson.toJson(json);
                File file = new File(shopDataDir, key + ".json");
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(jsonStr);
                } catch (IOException e) {
                    throw  new RuntimeException(e);
                }
            });
        };
    }

    public static Map<String, FPSMShop> loadShopData() {
        Map<String, FPSMShop> shops = new HashMap<>();
        Path gamePath = FMLLoader.getGamePath();
        File fpsmatchDir = new File(gamePath.toFile(), "fpsmatch");
        File shopDataDir = new File(fpsmatchDir, "shop");

        if (shopDataDir.exists() && shopDataDir.isDirectory()) {
            for (File file : Objects.requireNonNull(shopDataDir.listFiles())) {
                if (file.isFile() && file.getName().endsWith(".json")) {
                    try (FileReader reader = new FileReader(file)) {
                        JsonElement jsonElement = new Gson().fromJson(reader, JsonElement.class);
                        String key = file.getName().substring(0, file.getName().lastIndexOf('.'));
                        JsonObject jsonObject = jsonElement.getAsJsonObject();
                        Map<ShopData.ItemType, ArrayList<ShopData.ShopSlot>> data = FPSMCodec.decodeShopDataMapFromJson(jsonObject);
                        shops.put(key, new FPSMShop(key,new ShopData(data)));
                    } catch (IOException e) {
                        throw new RuntimeException("Error reading JSON file: " + file.getName(), e);
                    }
                }
            }
        }
        return shops;
    }

    public static void saveMaps(){
        Map<String, List<BaseMap>> maps = FPSMCore.getAllMaps();
        Path gamePath = FMLLoader.getGamePath();
        File fpsmatchDir = new File(gamePath.toFile(), "fpsmatch");
        if(checkOrCreateFile(fpsmatchDir)){
            File file = new File(fpsmatchDir,"spawnpoints.json");
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
        };
    }


    public static Map<ResourceLocation,Map<String,List<SpawnPointData>>> loadMaps(){
        Map<ResourceLocation,Map<String,List<SpawnPointData>>> data = new HashMap<>();
        Path gamePath = FMLLoader.getGamePath();
        File fpsmatchDir = new File(gamePath.toFile(), "fpsmatch");
        File file = new File(fpsmatchDir,"spawnpoints.json");
        if (file.exists()) {
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
