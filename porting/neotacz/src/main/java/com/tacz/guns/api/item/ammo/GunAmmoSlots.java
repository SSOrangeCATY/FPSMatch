package com.tacz.guns.api.item.ammo;

import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record GunAmmoSlots(List<GunAmmoSlot> slots, String activeSlotId) {
    public GunAmmoSlots {
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("slots must not be empty");
        }
        slots = List.copyOf(slots);
        Set<String> slotIds = new HashSet<>();
        for (GunAmmoSlot slot : slots) {
            if (!slotIds.add(slot.slotId())) {
                throw new IllegalArgumentException("slotId must be unique: " + slot.slotId());
            }
        }
        if (activeSlotId == null || activeSlotId.isBlank()) {
            activeSlotId = slots.getFirst().slotId();
        }
        String finalActiveSlotId = activeSlotId;
        boolean found = slots.stream().anyMatch(slot -> slot.slotId().equals(finalActiveSlotId));
        if (!found) {
            throw new IllegalArgumentException("activeSlotId must reference a known slot");
        }
    }

    public static GunAmmoSlots single(Identifier ammoId) {
        return new GunAmmoSlots(List.of(new GunAmmoSlot("main", ammoId, "main")), "main");
    }

    public GunAmmoSlot activeSlot() {
        return slots.stream()
                .filter(slot -> slot.slotId().equals(activeSlotId))
                .findFirst()
                .orElseThrow();
    }

    public GunAmmoSlots withActiveSlot(String slotId) {
        return new GunAmmoSlots(slots, slotId);
    }
}
