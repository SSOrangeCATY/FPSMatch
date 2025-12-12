
package com.phasetranscrystal.fpsmatch.common.client.screen.texture;

import net.minecraft.client.Minecraft;
import javax.annotation.Nullable;
import java.io.File;
import java.util.UUID;

public final class NamecardResolver {
    @FunctionalInterface public interface Provider{ @Nullable File resolve(UUID uuid); }
    private static Provider provider = uuid -> {
        File dir = new File(Minecraft.getInstance().gameDirectory,"fpsmatch/namecards");
        File png = new File(dir, uuid + ".png");
        return png.exists()?png:null;
    };
    public static void setProvider(Provider p){ provider = p==null?provider:p; }
    @Nullable public static File resolve(UUID uuid){ return provider.resolve(uuid); }
    private NamecardResolver(){}
}