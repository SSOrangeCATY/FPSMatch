package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.entity.sync.core.DataHolder;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, GunMod.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DataHolder>> SYNCED_ENTITY_DATA = ATTACHMENT_TYPES.register(
            "synced_entity_data",
            () -> AttachmentType.builder(DataHolder::new).serialize(new IAttachmentSerializer<>() {
                @Override
                public DataHolder read(IAttachmentHolder holder, ValueInput input) {
                    DataHolder dataHolder = new DataHolder();
                    input.read("Data", CompoundTag.CODEC).ifPresent(dataHolder::deserializeNBT);
                    return dataHolder;
                }

                @Override
                public boolean write(DataHolder attachment, ValueOutput output) {
                    CompoundTag tag = attachment.serializeNBT();
                    if (tag.getListOrEmpty("Entries").isEmpty()) {
                        return false;
                    }
                    output.store("Data", CompoundTag.CODEC, tag);
                    return true;
                }
            }).build()
    );
}
