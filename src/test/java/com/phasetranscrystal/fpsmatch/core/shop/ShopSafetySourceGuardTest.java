package com.phasetranscrystal.fpsmatch.core.shop;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopSafetySourceGuardTest {
    private static String readSource(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    @Test
    void shopMoneySyncDoesNotBroadcastIndividualMoneyToAllClients() throws IOException {
        String source = readSource("src/main/java/com/phasetranscrystal/fpsmatch/core/shop/FPSMShop.java");

        assertFalse(source.contains("PacketDistributor.ALL.noArg(), new ShopMoneyS2CPacket"));
        assertTrue(source.contains("PacketDistributor.PLAYER.with(() -> player), new ShopMoneyS2CPacket"));
    }

    @Test
    void shopUseRequiresAlivePlayer() throws IOException {
        String source = readSource("src/main/java/com/phasetranscrystal/fpsmatch/core/map/BaseMap.java");

        assertTrue(source.contains("player.isAlive()"));
    }

    @Test
    void inventoryDeliveryReportsWhetherTheStackWasAccepted() throws IOException {
        String source = readSource("src/main/java/com/phasetranscrystal/fpsmatch/util/FPSMUtil.java");
        String shopSlotSource = readSource("src/main/java/com/phasetranscrystal/fpsmatch/core/shop/slot/ShopSlot.java");

        assertTrue(source.contains("public static boolean addItemToPlayerInventory"));
        assertTrue(source.contains("player.getInventory().add"));
        assertTrue(source.contains("return accepted && itemStack.isEmpty()"));
        assertTrue(shopSlotSource.contains("FPSMUtil.playerDropMatchItem(player, itemStack"));
    }
}
