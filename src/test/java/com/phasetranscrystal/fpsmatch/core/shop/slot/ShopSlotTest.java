package com.phasetranscrystal.fpsmatch.core.shop.slot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopSlotTest {
    @Test
    void lockedSlotCannotBeBoughtEvenWhenUnderMaxBuyCount() {
        assertFalse(ShopSlotPurchaseRules.canBuy(100, 100, 1, 2, true));
    }

    @Test
    void unlimitedMoneyDoesNotBypassLockedSlot() {
        assertFalse(ShopSlotPurchaseRules.canBuy(-1, 100, 1, 2, true));
    }

    @Test
    void unlockedSlotCanBeBoughtWhenPlayerHasEnoughMoney() {
        assertTrue(ShopSlotPurchaseRules.canBuy(100, 100, 1, 2, false));
    }
}
