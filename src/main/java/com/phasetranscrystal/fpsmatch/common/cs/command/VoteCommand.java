package com.phasetranscrystal.fpsmatch.common.cs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.common.cs.map.CSGameMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

public class VoteCommand {
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("cs2").then(Commands.argument("action",StringArgumentType.string()).executes(context -> {
            String action = StringArgumentType.getString(context,"action");
            if(context.getSource().getEntity() instanceof ServerPlayer player){
                if(FPSMCore.getInstance().getMapByPlayer(player) instanceof CSGameMap csGameMap){
                    csGameMap.handleChatCommand(action,player);
                }else{
                    context.getSource().sendFailure(Component.translatable("command.cs.noMap"));
                }
            }else{
                context.getSource().sendFailure(Component.translatable("command.cs.onlyPlayer"));
            }
            return 1;
        }));

        dispatcher.register(literal);
    }
}
