package com.phasetranscrystal.fpsmatch.common.client.spec;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

public class SpecKeyHandler {
    public static final List<KeyMapping> switchKeys = new ArrayList<>();

    public static void registerSwitchKey(KeyMapping key) {
        switchKeys.add(key);
    }

    public static boolean switchKeyMatches(int keyCode,int scanCode){
        KeyEvent event = new KeyEvent(keyCode, scanCode, 0);
        for (KeyMapping key : switchKeys) {
            if (key.matches(event)){
                return true;
            }
        }
        return false;
    }
}
