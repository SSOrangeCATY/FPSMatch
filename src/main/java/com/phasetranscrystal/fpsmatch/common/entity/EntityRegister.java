package com.phasetranscrystal.fpsmatch.common.entity;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.FlashBombEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.GrenadeEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.IncendiaryGrenadeEntity;
import com.phasetranscrystal.fpsmatch.common.entity.throwable.SmokeShellEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class EntityRegister {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, FPSMatch.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<SmokeShellEntity>> SMOKE_SHELL =
            ENTITY_TYPES.register("smoke_shell", id -> EntityType.Builder.<SmokeShellEntity>of(SmokeShellEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredHolder<EntityType<?>, EntityType<IncendiaryGrenadeEntity>> INCENDIARY_GRENADE =
            ENTITY_TYPES.register("ct_incendiary_grenade", id -> EntityType.Builder.<IncendiaryGrenadeEntity>of(IncendiaryGrenadeEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeEntity>> GRENADE =
            ENTITY_TYPES.register("grenade", id -> EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredHolder<EntityType<?>, EntityType<FlashBombEntity>> FLASH_BOMB =
            ENTITY_TYPES.register("flash_bomb", id -> EntityType.Builder.<FlashBombEntity>of(FlashBombEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredHolder<EntityType<?>, EntityType<MatchDropEntity>> MATCH_DROP_ITEM =
            ENTITY_TYPES.register("match_drop", id -> EntityType.Builder.<MatchDropEntity>of(MatchDropEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

}
