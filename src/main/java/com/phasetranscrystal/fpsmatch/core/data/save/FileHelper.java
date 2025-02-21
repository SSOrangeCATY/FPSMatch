package com.phasetranscrystal.fpsmatch.core.data.save;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.codec.FPSMCodec;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
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
import java.io.IOException;
import java.util.*;

/**
 * 旧版本数据保存系统 用于读取旧版数据 不再参与存储功能
 * */
@Deprecated
public class FileHelper {
    public static final File FPS_MATCH_DIR = new File(FMLLoader.getGamePath().toFile(), "fpsmatch");

    public static String fixName(String fileName) {
        // 定义不能包含的特殊字符
        String[] specialChars = new String[]{"\\", "/", ":", "*", "?", "\"", "<", ">", "|"};
        // 遍历特殊字符数组
        for (String charToReplace : specialChars) {
            // 替换特殊字符为空字符串
            fileName = fileName.replace(charToReplace, "");
        }
        // 打印或返回处理后的文件名
        return fileName;
    }

    @Deprecated
    public static List<RawMapData> loadMaps(String levelName) {
        levelName = fixName(levelName);
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
                                File shopFile = new File(mapDir, "shop");
                                if (shopFile.exists() && shopFile.isDirectory()) {
                                    Map<String,Map<ItemType, ArrayList<ShopSlot>> > shopMap = new HashMap<>();
                                    for (File shopDataFile : Objects.requireNonNull(shopFile.listFiles())) {
                                        if (shopDataFile.isFile()) {
                                            try (FileReader shopReader = new FileReader(shopDataFile)) {
                                                JsonElement shopJson = new Gson().fromJson(shopReader, JsonElement.class);
                                                Map<ItemType, ArrayList<ShopSlot>> rawData = FPSMCodec.decodeShopDataMapFromJson(shopJson);
                                                shopMap.put(shopDataFile.getName().replace(".json",""),rawData);
                                            } catch (IOException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }
                                    rawMapData.setShop(shopMap);
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
                                        Map<String,ArrayList<ItemStack>> startKitList = FPSMCodec.decodeTeamKitsFromJson(startKitJson);
                                        rawMapData.setStartKits(startKitList);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                File teleportPoint = new File(mapDir, "matchEndTeleportPoint.json");
                                if (teleportPoint.exists() && teleportPoint.isFile()) {
                                    try (FileReader teleportPointReader = new FileReader(teleportPoint)) {
                                        JsonElement teleportPointJson = new Gson().fromJson(teleportPointReader, JsonElement.class);
                                        SpawnPointData rawData =  FPSMCodec.SPAWN_POINT_DATA_CODEC.decode(JsonOps.INSTANCE, teleportPointJson).getOrThrow(false, e -> { throw new RuntimeException(e); }).getFirst();
                                        rawMapData.setMatchEndTeleportPoint(rawData);
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
        @Nullable public Map<String,Map<ItemType, ArrayList<ShopSlot>>> shop;
        @Nullable public List<AreaData> blastAreaDataList;
        @Nullable public Map<String,ArrayList<ItemStack>> startKits;
        @Nullable public SpawnPointData matchEndTeleportPoint;

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

        public void setShop(@Nullable Map<String,Map<ItemType, ArrayList<ShopSlot>>> shop) {
            this.shop = shop;
        }

        public void setMatchEndTeleportPoint(@Nullable SpawnPointData matchEndTeleportPoint) {
            this.matchEndTeleportPoint = matchEndTeleportPoint;
        }

    }
}
