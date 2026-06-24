package com.phasetranscrystal.fpsmatch.common.client.renderer;

import com.phasetranscrystal.fpsmatch.common.entity.MatchDropEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import org.jetbrains.annotations.NotNull;

public class MatchDropRenderer implements EntityRendererProvider<MatchDropEntity> {
    @Override
    public @NotNull EntityRenderer<MatchDropEntity, ?> create(@NotNull Context context) {
        return new ThrownItemRenderer<>(context);
    }
}
