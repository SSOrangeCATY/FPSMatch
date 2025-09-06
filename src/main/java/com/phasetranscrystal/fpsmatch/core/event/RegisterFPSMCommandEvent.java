package com.phasetranscrystal.fpsmatch.core.event;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.List;

public class RegisterFPSMCommandEvent extends Event {
    private final LiteralArgumentBuilder<CommandSourceStack> builder;
    private final List<Component> helps = new ArrayList<>();

    public RegisterFPSMCommandEvent(LiteralArgumentBuilder<CommandSourceStack> builder) {
        this.builder = builder;
    }

    public void addChild(LiteralArgumentBuilder<CommandSourceStack> child) {
        this.builder.then(child);
    }

    public void addHelp(Component translation) {
        this.helps.add(translation);
    }

    public LiteralArgumentBuilder<CommandSourceStack> get(){
        return this.builder;
    }

    public List<Component> getHelps() {
        return new ArrayList<>(this.helps);
    }
}
