package com.phasetranscrystal.fpsmatch.core.event.register;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegisterFPSMCommandEvent extends Event {
    private final LiteralArgumentBuilder<CommandSourceStack> builder;
    private final Map<FPSMCommand.HelpCategory, List<Component>> helps = new HashMap<>();
    private final CommandBuildContext context;

    public RegisterFPSMCommandEvent(LiteralArgumentBuilder<CommandSourceStack> builder , CommandBuildContext context) {
        this.builder = builder;
        this.context = context;
    }

    public void addChild(LiteralArgumentBuilder<CommandSourceStack> child) {
        this.builder.then(child);
    }

    public void addHelp(FPSMCommand.HelpCategory category, MutableComponent translation) {
        this.helps.computeIfAbsent(category, k -> new ArrayList<>()).add(translation.append(Component.literal("\n")));
    }

    public void addHelp(FPSMCommand.HelpCategory category, List<MutableComponent> translations) {
        for (MutableComponent component : translations) {
            addHelp(category, component);
        }
    }

    public CommandBuildContext getContext(){
        return this.context;
    }

    public LiteralArgumentBuilder<CommandSourceStack> getTree(){
        return this.builder;
    }

    public Map<FPSMCommand.HelpCategory, List<Component>> getHelps() {
        return this.helps;
    }
}
