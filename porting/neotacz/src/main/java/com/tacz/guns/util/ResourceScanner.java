package com.tacz.guns.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.tacz.guns.GunMod;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;

public class ResourceScanner {
    /**
     * 扫描指定目录下的所有json文件<br>
     * 与原版的scanDirectory方法的区别在于，查询结果是作为返回值返回的，而且允许注释
     * 对于相同的文件路径，只读取优先级最高的文件
     * @param pResourceManager 资源管理器
     * @param pName 目录名
     * @param pGson Gson实例
     * @return 扫描到的json文件
     */
    public static Map<Identifier, JsonElement> scanDirectory(ResourceManager pResourceManager, String pName, Gson pGson) {
        return scanDirectory(pResourceManager, FileToIdConverter.json(pName), pGson);
    }

    public static Map<Identifier, JsonElement> scanDirectory(ResourceManager pResourceManager, FileToIdConverter filetoidconverter, Gson pGson) {
        Map<Identifier, JsonElement> output = Maps.newHashMap();
        for(Map.Entry<Identifier, Resource> entry : filetoidconverter.listMatchingResources(pResourceManager).entrySet()) {
            Identifier resourcelocation = entry.getKey();
            Identifier resourcelocation1 = filetoidconverter.fileToId(resourcelocation);

            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement jsonelement = parseLenientJsonElement(pGson, reader);
                JsonElement jsonelement1 = output.put(resourcelocation1, jsonelement);
                if (jsonelement1 != null) {
                    throw new IllegalStateException("Duplicate data file ignored with ID " + resourcelocation1);
                }
            } catch (IllegalArgumentException | IOException | JsonParseException jsonparseexception) {
                GunMod.LOGGER.error("Couldn't parse data file {} from {}", resourcelocation1, resourcelocation, jsonparseexception);
            }
        }
        return output;
    }

    public static JsonElement parseLenientJsonElement(Gson gson, Reader reader) {
        JsonReader jsonReader = new JsonReader(reader);
        jsonReader.setStrictness(Strictness.LENIENT);
        return gson.fromJson(jsonReader, JsonElement.class);
    }

    public static Map<Identifier, Identifier> scanDirectoryResources(ResourceManager pResourceManager, FileToIdConverter filetoidconverter) {
        Map<Identifier, Identifier> output = Maps.newHashMap();
        for (Map.Entry<Identifier, Resource> entry : filetoidconverter.listMatchingResources(pResourceManager).entrySet()) {
            Identifier rawLocation = entry.getKey();
            Identifier id = filetoidconverter.fileToId(rawLocation);
            Identifier old = output.put(id, rawLocation);
            if (old != null) {
                throw new IllegalStateException("Duplicate data file ignored with ID " + id);
            }
        }
        return output;
    }

    /**
     * 扫描指定目录下的所有json文件<br/>
     * 与{@link #scanDirectory(ResourceManager, String, Gson)}不同的是，该方法会读取所有json文件作为列表返回
     * @param pResourceManager 资源管理器
     * @param filetoidconverter 文件路径和id的映射
     * @param pGson Gson实例
     * @return 扫描到的json文件
     */
    public static Map<Identifier, List<JsonElement>> scanDirectoryAll(ResourceManager pResourceManager, FileToIdConverter filetoidconverter, Gson pGson) {
        Map<Identifier, List<JsonElement>> output = Maps.newHashMap();
        for(Map.Entry<Identifier, List<Resource>> entry : filetoidconverter.listMatchingResourceStacks(pResourceManager).entrySet()) {
            Identifier resourcelocation = entry.getKey();
            Identifier resourcelocation1 = filetoidconverter.fileToId(resourcelocation);

            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonElement jsonelement = parseLenientJsonElement(pGson, reader);
                    List<JsonElement> list = output.computeIfAbsent(resourcelocation1, k -> Lists.newArrayList());
                    list.add(jsonelement);
                } catch (IllegalArgumentException | IOException | JsonParseException jsonparseexception) {
                    GunMod.LOGGER.error("Couldn't parse data file {} from {}", resourcelocation1, resourcelocation, jsonparseexception);
                }
            }
        }
        return output;
    }
}
