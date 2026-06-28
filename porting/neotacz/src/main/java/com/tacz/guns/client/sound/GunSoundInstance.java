package com.tacz.guns.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

public class GunSoundInstance extends AbstractSoundInstance {
    private static final FileToIdConverter TACZ_SOUND_LISTER = new FileToIdConverter("tacz_sounds", ".ogg");

    @Nullable
    private final Identifier registryName;
    private final boolean canPlay;
    @Nullable
    private Sound redirectedSound;

    public GunSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, Entity entity, int soundDistance, Identifier registryName, boolean mono) {
        this(soundEvent, source, volume, pitch, entity, soundDistance, registryName, mono, false);
    }

    public GunSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, Entity entity, int soundDistance, Identifier registryName, boolean mono, boolean relative) {
        super(soundEvent, source, RandomSource.create(943));
        this.attenuation = Attenuation.NONE;
        this.relative = relative;
        this.registryName = registryName;
        this.canPlay = !entity.isSilent();
        this.volume = volume;
        this.pitch = pitch;
        this.x = relative ? 0 : entity.getX();
        this.y = relative ? 0 : entity.getY();
        this.z = relative ? 0 : entity.getZ();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && !relative) {
            this.volume = volume * (1.0F - Math.min(1.0F, (float) Math.sqrt(player.distanceToSqr(x, y, z)) / soundDistance));
            this.volume *= this.volume;
        }
    }

    public void setStop() {
        Minecraft.getInstance().getSoundManager().stop(this);
    }

    @Override
    public boolean canPlaySound() {
        return this.canPlay;
    }

    @Nullable
    public Identifier getRegistryName() {
        return registryName;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        WeighedSoundEvents events = super.resolve(manager);
        if (events != null && this.registryName != null) {
            this.redirectedSound = new TaczSound(this.registryName, TACZ_SOUND_LISTER.idToFile(this.registryName), super.getSound());
            this.sound = this.redirectedSound;
        } else {
            this.redirectedSound = null;
        }
        return events;
    }

    @Override
    public Sound getSound() {
        return redirectedSound == null ? super.getSound() : redirectedSound;
    }

    private static class TaczSound extends Sound {
        private final Identifier location;
        private final Identifier path;

        private TaczSound(Identifier location, Identifier path, Sound template) {
            super(location, template.getVolume(), template.getPitch(), template.getWeight(), Type.FILE,
                    template.shouldStream(), false, template.getAttenuationDistance());
            this.location = location;
            this.path = path;
        }

        @Override
        public Identifier getLocation() {
            return location;
        }

        @Override
        public Identifier getPath() {
            return path;
        }
    }
}
