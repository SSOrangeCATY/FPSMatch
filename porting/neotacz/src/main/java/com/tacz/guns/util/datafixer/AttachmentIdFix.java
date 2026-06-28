package com.tacz.guns.util.datafixer;

import com.google.common.collect.ImmutableMap;
import com.tacz.guns.api.DefaultAssets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import java.util.Map;

import static com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor.*;
import static com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor.ATTACHMENT_ID_TAG;

public final class AttachmentIdFix {
    private AttachmentIdFix() {
    }

    public static final Map<Identifier, Identifier> OLD_TO_NEW;
    static  {
        OLD_TO_NEW = ImmutableMap.<Identifier, Identifier>builder()
                .put(Identifier.fromNamespaceAndPath("tacz", "muzzle_silence_knight_qd"), Identifier.fromNamespaceAndPath("tacz", "muzzle_silencer_knight_qd"))
                .put(Identifier.fromNamespaceAndPath("tacz", "muzzle_silence_mirage"), Identifier.fromNamespaceAndPath("tacz", "muzzle_silencer_mirage"))
                .put(Identifier.fromNamespaceAndPath("tacz", "muzzle_silence_phantom_s1"), Identifier.fromNamespaceAndPath("tacz", "muzzle_silencer_phantom_s1"))
                .put(Identifier.fromNamespaceAndPath("tacz", "muzzle_silence_ptilopsis"), Identifier.fromNamespaceAndPath("tacz", "muzzle_silencer_ptilopsis"))
                .put(Identifier.fromNamespaceAndPath("tacz", "muzzle_silence_ursus"), Identifier.fromNamespaceAndPath("tacz", "muzzle_silencer_ursus"))
                .put(Identifier.fromNamespaceAndPath("tacz", "muzzle_silence_vulture"), Identifier.fromNamespaceAndPath("tacz", "muzzle_silencer_vulture"))
                .build();
    }

    // 预留 boolean 返回值用于未来使用，"尽可能避免使用 void ，除非这个操作不能提供任何有用的信息"
    public static boolean updateAttachmentIdInTag(CompoundTag tag) {
        Identifier old = getAttachmentIdFromTag(tag);
        if (!old.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            Identifier fixed = updateAttachmentId(old);
            if (!old.equals(fixed)) {
                tag.putString(ATTACHMENT_ID_TAG, fixed.toString());
                return true;
            }
        }
        return false;
    }

    public static Identifier updateAttachmentId(Identifier old) {
        return OLD_TO_NEW.getOrDefault(old, old);
    }
}
