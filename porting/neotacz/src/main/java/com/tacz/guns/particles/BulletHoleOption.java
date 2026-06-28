package com.tacz.guns.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.init.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class BulletHoleOption implements ParticleOptions {
    public static final MapCodec<BulletHoleOption> CODEC = RecordCodecBuilder.mapCodec(builder ->
            builder.group(Codec.INT.fieldOf("dir").forGetter(option -> option.direction.ordinal()),
                    Codec.LONG.fieldOf("pos").forGetter(option -> option.pos.asLong()),
                    Codec.STRING.fieldOf("ammo_id").forGetter(option -> option.ammoId),
                    Codec.STRING.fieldOf("gun_id").forGetter(option -> option.gunId),
                    Codec.STRING.optionalFieldOf("gun_display_id", DefaultAssets.DEFAULT_GUN_DISPLAY_ID.toString()).forGetter(option -> option.gunDisplayId)
            ).apply(builder, BulletHoleOption::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BulletHoleOption> STREAM_CODEC = StreamCodec.of(
            (buffer, option) -> {
                buffer.writeEnum(option.direction);
                buffer.writeBlockPos(option.pos);
                buffer.writeUtf(option.ammoId);
                buffer.writeUtf(option.gunId);
                buffer.writeUtf(option.gunDisplayId);
            },
            buffer -> new BulletHoleOption(buffer.readEnum(Direction.class), buffer.readBlockPos(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf())
    );

    private final Direction direction;
    private final BlockPos pos;
    private final String ammoId;
    private final String gunId;
    private final String gunDisplayId;

    public BulletHoleOption(int dir, long pos, String ammoId, String gunId, String gunDisplayId) {
        this.direction = Direction.values()[dir];
        this.pos = BlockPos.of(pos);
        this.ammoId = ammoId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
    }

    public BulletHoleOption(Direction dir, BlockPos pos, String ammoId, String gunId, String gunDisplayId) {
        this.direction = dir;
        this.pos = pos;
        this.ammoId = ammoId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public String getAmmoId() {
        return ammoId;
    }

    public String getGunId() {
        return gunId;
    }

    public String getGunDisplayId() {
        return gunDisplayId;
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.BULLET_HOLE.get();
    }

    public String writeToString() {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(this.getType()) + " " + this.direction.getName();
    }
}
