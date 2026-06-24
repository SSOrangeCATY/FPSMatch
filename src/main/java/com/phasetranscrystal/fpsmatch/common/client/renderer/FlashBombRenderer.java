package com.phasetranscrystal.fpsmatch.common.client.renderer;

import com.phasetranscrystal.fpsmatch.common.entity.throwable.FlashBombEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import org.jetbrains.annotations.NotNull;

public class FlashBombRenderer implements EntityRendererProvider<FlashBombEntity> {
    @Override
    public @NotNull EntityRenderer<FlashBombEntity, ?> create(@NotNull Context context) {
        return new ThrownItemRenderer<>(context);
    }
}
