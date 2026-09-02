package com.ptcrys.fpsmatch.common.client.screen.ldlib2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ConcurrentModificationException;

/** Keeps a stale LDLib2 screen from crashing while Minecraft tears down the client. */
public final class Ldlib2RenderGuard {

    private static final Logger LOGGER = LogUtils.getLogger();

    private Ldlib2RenderGuard() {}

    public static boolean shouldSkip(Screen owner) {
        Minecraft minecraft = Minecraft.getInstance();
        var integratedServer = minecraft.getSingleplayerServer();
        return lifecycleEnded(
                minecraft.isRunning(),
                minecraft.screen == owner,
                minecraft.getConnection() != null,
                integratedServer != null,
                integratedServer == null || integratedServer.isRunning());
    }

    public static boolean ignoreConcurrentModification(
                                                       Screen owner,
                                                       ConcurrentModificationException failure) {
        if (!shouldSkip(owner)) {
            return false;
        }
        LOGGER.debug("Skipping stale LDLib2 render during client teardown", failure);
        return true;
    }

    static boolean lifecycleEnded(
                                  boolean clientRunning,
                                  boolean screenCurrent,
                                  boolean connectionPresent,
                                  boolean integratedServerPresent,
                                  boolean integratedServerRunning) {
        return !clientRunning || !screenCurrent || !connectionPresent || integratedServerPresent && !integratedServerRunning;
    }
}
