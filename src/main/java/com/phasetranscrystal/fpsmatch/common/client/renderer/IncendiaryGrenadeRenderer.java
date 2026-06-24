package com.phasetranscrystal.fpsmatch.common.client.renderer;

import com.phasetranscrystal.fpsmatch.common.entity.throwable.IncendiaryGrenadeEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import org.jetbrains.annotations.NotNull;

public class IncendiaryGrenadeRenderer implements EntityRendererProvider<IncendiaryGrenadeEntity> {
    @Override
    public @NotNull EntityRenderer<IncendiaryGrenadeEntity, ?> create(@NotNull Context context) {
        return new ThrownItemRenderer<>(context);
    }
}
