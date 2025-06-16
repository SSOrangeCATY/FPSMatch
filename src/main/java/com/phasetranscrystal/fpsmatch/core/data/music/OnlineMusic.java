package com.phasetranscrystal.fpsmatch.core.data.music;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.core.data.HashData;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.util.hash.FileHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public record OnlineMusic(String uuid, String musicUrl, String musicName, String coverUrl, String customMemo, HashData hashData) {
    // Thread pool for async operations
    private static final ExecutorService downloadExecutor = Executors.newCachedThreadPool();

    public static final Codec<OnlineMusic> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("uuid").forGetter(OnlineMusic::uuid),
            Codec.STRING.fieldOf("musicUrl").forGetter(OnlineMusic::musicUrl),
            Codec.STRING.fieldOf("musicName").forGetter(OnlineMusic::musicName),
            Codec.STRING.fieldOf("coverUrl").forGetter(OnlineMusic::coverUrl),
            Codec.STRING.fieldOf("customMemo").forGetter(OnlineMusic::customMemo),
            HashData.CODEC.fieldOf("hashData").forGetter(OnlineMusic::hashData)
    ).apply(instance, OnlineMusic::new));

    private static final Logger log = LoggerFactory.getLogger(OnlineMusic.class);

    public File getMusicFile() {
        return FPSMDataManager.getLocalCacheFile(musicName, "music");
    }

    public File getCoverFile() {
        return FPSMDataManager.getLocalCacheFile(musicName, "cover");
    }

    public boolean musicExists() {
        return getMusicFile().exists();
    }

    public boolean coverExists(){
        return getCoverFile().exists();
    }

    public CompletableFuture<Boolean> downloadMusic() {
        return downloadAsync(getMusicFile(), musicUrl);
    }

    public CompletableFuture<Boolean> downloadCover() {
        return downloadAsync(getCoverFile(), coverUrl);
    }

    public boolean checkHash() {
        File file = getMusicFile();
        if(file.exists()){
            try{
                return FileHashUtil.calculateHash(getMusicFile(), hashData.getHashAlgorithm()).equals(hashData.hash());
            }catch (Exception e){
                log.error("error: ", e);
                return false;
            }
        }else{
            return false;
        }
    }

    private CompletableFuture<Boolean> downloadAsync(File file, String downloadUrl) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);

                int code = connection.getResponseCode();
                if (code == 200) {
                    try (InputStream in = connection.getInputStream();
                         FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = in.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                        }
                    }
                    log.info("Download successful => {}", file.getAbsolutePath());
                    if(checkHash()){
                        return true;
                    }else{
                        // 删除文件
                        file.delete();
                        return false;
                    }
                } else {
                    log.error("Download failed -> code:{} / {}", code, downloadUrl);
                    return false;
                }
            } catch (IOException e) {
                log.error("error: ", e);
                return false;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, downloadExecutor);
    }

    public InputStream stream(){
        if(!this.checkHash()) return null;

        try(FileInputStream fis = new FileInputStream(this.getMusicFile());
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while((len=fis.read(buf))!=-1){
                byteArrayOutputStream.write(buf,0,len);
            }
            return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        }catch (Exception e){
            log.error("error: ", e);
            return null;
        }
    }

    public static void shutdown() {
        downloadExecutor.shutdown();
    }
}