package com.phasetranscrystal.fpsmatch.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMCommandEvent;
import com.phasetranscrystal.fpsmatch.core.map.*;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.*;

import static com.phasetranscrystal.fpsmatch.common.command.FPSMCommandSuggests.*;

public class FPSMCommand {
    // 常量
    private static List<Component> HELPS;

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> tree = Commands.literal("fpsm")
                .requires(permission -> permission.hasPermission(2))
                .then(Commands.literal("help").executes(FPSMCommand::handleHelp));
        CommandBuildContext context = event.getBuildContext();

        Pair<LiteralArgumentBuilder<CommandSourceStack>, CommandBuildContext> builder = Pair.of(tree,context);

        LiteralArgumentBuilder<CommandSourceStack> literal = init(builder);


        RegisterFPSMCommandEvent registerFPSMCommandEvent = new RegisterFPSMCommandEvent(literal,context);

        MinecraftForge.EVENT_BUS.post(registerFPSMCommandEvent);
        HELPS = registerFPSMCommandEvent.getHelps();
        dispatcher.register(registerFPSMCommandEvent.getTree());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> init(Pair<LiteralArgumentBuilder<CommandSourceStack>, CommandBuildContext> builder) {
        FPSMBaseCommand.init(builder.getFirst());
        FPSMapCommand.init(builder);
        return builder.getFirst();
    }

    private static int handleHelp(CommandContext<CommandSourceStack> context) {
        MutableComponent helpMessage = Component.translatable("commands.fpsm.help.header")
                .append(Component.literal("\n"))
                .append(Component.translatable("commands.fpsm.help.basic"))
                .append(Component.literal("\n"))
                .append(Component.translatable("commands.fpsm.help.listener"))
                .append(Component.literal("\n"))
                .append(Component.translatable("commands.fpsm.help.shop"))
                .append(Component.literal("\n"))
                .append(Component.translatable("commands.fpsm.help.map"))
                .append(Component.literal("\n"));
        for (Component help : HELPS) {
            helpMessage.append(help).append(Component.literal("\n"));
        }
        helpMessage.append(Component.translatable("commands.fpsm.help.footer"));

        context.getSource().sendSuccess(() -> helpMessage, false);
        return 1;
    }

    // 辅助方法
    public static Optional<BaseMap> getMapByName(CommandContext<CommandSourceStack> context) {
        String mapName = StringArgumentType.getString(context, MAP_NAME_ARG);
        return FPSMCore.getInstance().getMapByName(mapName);
    }

    public static Optional<ServerTeam> getTeamByName(CommandContext<CommandSourceStack> context) {
        String teamName = StringArgumentType.getString(context, TEAM_NAME_ARG);
        Optional<BaseMap> mapOpt = getMapByName(context);
        if (mapOpt.isPresent()) {
           return mapOpt.get().getMapTeams().getTeamByName(teamName);
        }else{
            return Optional.empty();
        }
    }

    public static <T extends TeamCapability> Optional<T> getCapability(CommandContext<CommandSourceStack> context , Class<T> clazz) {
        Optional<ServerTeam> team = FPSMCommand.getTeamByName(context);
        return team.flatMap(serverTeam -> serverTeam.getCapability(clazz));
    }

    public static Optional<FPSMShop<?>> getShop(CommandContext<CommandSourceStack> context, BaseMap map, String shopName) {
        if (map instanceof ShopMap<?> shopMap) {
            return shopMap.getShop(shopName);
        }
        return Optional.empty();
    }

    public static void sendSuccess(CommandSourceStack source, Component key) {
        source.sendSuccess(() -> key, true);
    }

    public static void sendFailure(CommandSourceStack source, Component key) {
        source.sendFailure(key);
    }

    public static boolean isPlayer(CommandSourceStack source) {
        return source.getEntity() instanceof Player;
    }

    public static ServerPlayer getPlayerOrFail(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!isPlayer(context.getSource())) {
            sendFailure(context.getSource(), Component.translatable("commands.fpsm.only.player"));
        }
        return context.getSource().getPlayerOrException();
    }
}