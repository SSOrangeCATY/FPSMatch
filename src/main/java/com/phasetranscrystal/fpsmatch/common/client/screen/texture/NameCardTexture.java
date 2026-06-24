
package com.phasetranscrystal.fpsmatch.common.client.screen.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class NameCardTexture extends SimpleTexture {
    private final File file;
    public int realWidth = 256, realHeight = 256;

    public NameCardTexture(File file, Identifier id){ super(id); this.file = file; }

    @Override public @NotNull TextureContents loadContents(ResourceManager rm) {
        try{
            BufferedImage img = ImageIO.read(file);
            if(img==null) throw new IOException("ImageIO.read returned null");
            realWidth = img.getWidth(); realHeight = img.getHeight();
            NativeImage ni = new NativeImage(NativeImage.Format.RGBA, realWidth, realHeight, false);
            for(int y=0;y<realHeight;y++) for(int x=0;x<realWidth;x++)
                ni.setPixel(x,y,img.getRGB(x,y));
            return new TextureContents(ni, null);
        }catch(IOException ex){
            NativeImage fb = new NativeImage(NativeImage.Format.RGBA,1,1,false);
            fb.setPixel(0,0,0xFFFF00FF);
            return new TextureContents(fb, null);
        }
    }
}
