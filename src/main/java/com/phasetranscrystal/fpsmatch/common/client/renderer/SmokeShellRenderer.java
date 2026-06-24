package com.phasetranscrystal.fpsmatch.common.client.renderer;

import com.phasetranscrystal.fpsmatch.common.entity.throwable.SmokeShellEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import org.jetbrains.annotations.NotNull;

public class SmokeShellRenderer implements EntityRendererProvider<SmokeShellEntity> {
    @Override
    public @NotNull EntityRenderer<SmokeShellEntity, ?> create(@NotNull Context context) {
        return new ThrownItemRenderer<>(context);
    }
}
