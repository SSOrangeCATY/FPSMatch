package com.phasetranscrystal.fpsmatch.util;

public class RenderUtil {
    public static int color(int r,int g,int b){
        return (((0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | ((b & 0xFF)));
    }

    public static int color(int r,int g,int b,int a){
        return (((a) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | ((b & 0xFF)));
    }
}
