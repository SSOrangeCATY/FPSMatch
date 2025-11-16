package com.phasetranscrystal.fpsmatch.core.event;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.List;

public class RegisterFPSMCommandEvent extends Event {
    private final LiteralArgumentBuilder<CommandSourceStack> builder;
    private final List<Component> helps = new ArrayList<>();
    private final CommandBuildContext context;

    public RegisterFPSMCommandEvent(LiteralArgumentBuilder<CommandSourceStack> builder , CommandBuildContext context) {
        this.builder = builder;
        this.context = context;
    }

    public void addChild(LiteralArgumentBuilder<CommandSourceStack> child) {
        this.builder.then(child);
    }

    public void addHelp(Component translation) {
        this.helps.add(translation);
    }

    public CommandBuildContext getContext(){
        return this.context;
    }

    public LiteralArgumentBuilder<CommandSourceStack> getTree(){
        return this.builder;
    }

    public List<Component> getHelps() {
        return new ArrayList<>(this.helps);
    }
}
