package com.phasetranscrystal.fpsmatch.common.client.renderer;

import com.phasetranscrystal.fpsmatch.common.entity.throwable.GrenadeEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import org.jetbrains.annotations.NotNull;

public class GrenadeRenderer implements EntityRendererProvider<GrenadeEntity> {
    @Override
    public @NotNull EntityRenderer<GrenadeEntity, ?> create(@NotNull Context context) {
        return new ThrownItemRenderer<>(context);
    }
}
