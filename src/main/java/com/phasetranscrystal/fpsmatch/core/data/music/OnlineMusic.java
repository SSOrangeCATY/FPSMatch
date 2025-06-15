package com.phasetranscrystal.fpsmatch.core.data.music;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public record OnlineMusic(String uuid, String musicUrl, String musicName, String coverUrl, String customMemo) {
    public static final Codec<OnlineMusic> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("uuid").forGetter(OnlineMusic::uuid),
            Codec.STRING.fieldOf("musicUrl").forGetter(OnlineMusic::musicUrl),
            Codec.STRING.fieldOf("musicName").forGetter(OnlineMusic::musicName),
            Codec.STRING.fieldOf("coverUrl").forGetter(OnlineMusic::coverUrl),
            Codec.STRING.fieldOf("customMemo").forGetter(OnlineMusic::customMemo)
            ).apply(instance, OnlineMusic::new));

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

    public void downloadMusic() {
        download(getMusicFile(), musicUrl);
    }

    public void downloadCover() {
        download(getCoverFile(), coverUrl);
    }

    private void download(File file, String coverUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(coverUrl).openConnection();
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
                System.out.println("download successful => " + file.getAbsolutePath());
            } else {
                System.out.println("download fail -> code:" + code + " / " + coverUrl);
            }
        } catch (IOException e) {
            e.fillInStackTrace();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}