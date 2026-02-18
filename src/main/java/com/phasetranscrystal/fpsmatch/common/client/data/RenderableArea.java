package com.phasetranscrystal.fpsmatch.common.client.data;

import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.network.chat.Component;

public record RenderableArea(Component name, AreaData area) {
}
