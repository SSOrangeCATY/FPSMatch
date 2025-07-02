package com.phasetranscrystal.fpsmatch.core.event;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.eventbus.api.Event;

public class RegisterFPSMCommandEvent extends Event {
    private final LiteralArgumentBuilder<CommandSourceStack> builder;

    public RegisterFPSMCommandEvent(LiteralArgumentBuilder<CommandSourceStack> builder) {
        this.builder = builder;
    }

    public void addChild(LiteralArgumentBuilder<CommandSourceStack> child) {
        this.builder.then(child);
    }

    public LiteralArgumentBuilder<CommandSourceStack> get(){
        return this.builder;
    }
}
