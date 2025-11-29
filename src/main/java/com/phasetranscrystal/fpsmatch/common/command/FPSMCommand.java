package com.phasetranscrystal.fpsmatch.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.event.register.RegisterFPSMCommandEvent;
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
    private static final Map<HelpCategory, List<Component>> HELPS = loadHelpEntries();

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> tree = Commands.literal("fpsm")
                .requires(permission -> permission.hasPermission(2))
                .then(Commands.literal("help")
                        .executes(FPSMCommand::handleHelp)
                        .then(Commands.literal("category")
                                .then(Commands.argument("category", StringArgumentType.string())
                                        .suggests(FPSMCommandSuggests.HELP_CATEGORIES_SUGGESTION)
                                        .executes(FPSMCommand::handleHelpWithCategory)
                                        .then(Commands.literal("page")
                                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                        .executes(FPSMCommand::handleHelpWithCategoryAndPage)))))
                        .then(Commands.literal("page")
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(FPSMCommand::handleHelpWithPage)))
                        .then(Commands.literal("capability")
                                .then(Commands.literal("map")
                                        .executes(FPSMCommand::handleMapCapabilityHelp)
                                        .then(Commands.literal("page")
                                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                        .executes(c -> handleCapabilityHelp(c, true, IntegerArgumentType.getInteger(c, "page"))))))
                                .then(Commands.literal("team")
                                        .executes(FPSMCommand::handleTeamCapabilityHelp)
                                        .then(Commands.literal("page")
                                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                        .executes(c -> handleCapabilityHelp(c, false, IntegerArgumentType.getInteger(c, "page"))))))
                        ));
        CommandBuildContext context = event.getBuildContext();

        Pair<LiteralArgumentBuilder<CommandSourceStack>, CommandBuildContext> builder = Pair.of(tree,context);

        LiteralArgumentBuilder<CommandSourceStack> literal = init(builder);

        RegisterFPSMCommandEvent registerFPSMCommandEvent = new RegisterFPSMCommandEvent(literal,context);

        MinecraftForge.EVENT_BUS.post(registerFPSMCommandEvent);
        addonHelpEntries(registerFPSMCommandEvent.getHelps());
        dispatcher.register(registerFPSMCommandEvent.getTree());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> init(Pair<LiteralArgumentBuilder<CommandSourceStack>, CommandBuildContext> builder) {
        FPSMBaseCommand.init(builder.getFirst());
        FPSMapCommand.init(builder);
        return builder.getFirst();
    }


    private static final int HELP_PER_PAGE = 16;

    // 帮助类别枚举
    public enum HelpCategory {
        BASIC, LISTENER, MAP
    }

    private static int handleHelp(CommandContext<CommandSourceStack> context) {
        return handleHelpWithPageNumber(context, 1, null);
    }

    private static int handleHelpWithPage(CommandContext<CommandSourceStack> context) {
        int page = IntegerArgumentType.getInteger(context, "page");
        return handleHelpWithPageNumber(context, page, null);
    }

    private static int handleHelpWithPageNumber(CommandContext<CommandSourceStack> context, int page) {
        return handleHelpWithPageNumber(context, page, null);
    }

    private static int handleHelpWithPageNumber(CommandContext<CommandSourceStack> context, int page, HelpCategory category) {
        MutableComponent helpMessage = Component.translatable("commands.fpsm.help.header")
                .append(Component.literal("\n"));

        List<Component> allHelpEntries;

        if(category != null) {
             allHelpEntries = HELPS.get(category);
        }else{
            allHelpEntries = new ArrayList<>();
            for (List<Component> helpList : HELPS.values()) {
                allHelpEntries.addAll(helpList);
            }
        }

        // 计算分页
        int totalPages = (int) Math.ceil((double) allHelpEntries.size() / HELP_PER_PAGE);
        int startIndex = (page - 1) * HELP_PER_PAGE;
        int endIndex = Math.min(startIndex + HELP_PER_PAGE, allHelpEntries.size());

        // 添加当前页的帮助内容
        for (int i = startIndex; i < endIndex; i++) {
            helpMessage.append(allHelpEntries.get(i));
        }

        // 添加分页信息
        helpMessage.append(Component.translatable("commands.fpsm.help.page_info", page, totalPages))
                .append(Component.literal("\n"))
                .append(Component.translatable("commands.fpsm.help.footer"));

        context.getSource().sendSuccess(() -> helpMessage, false);
        return 1;
    }


    private static void addonHelpEntries(Map<HelpCategory, List<Component>> helpEntries) {
        for (Map.Entry<HelpCategory, List<Component>> entry : helpEntries.entrySet()) {
            HELPS.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
        }
    }
    /**
     * 从语言文件加载帮助条目
     */
    private static Map<HelpCategory, List<Component>> loadHelpEntries() {
        Map<HelpCategory, List<Component>> helpEntries = new HashMap<>();

        List<Component> basicHelpEntries = new ArrayList<>();
        basicHelpEntries.add(Component.translatable("commands.fpsm.help.category.basic").append(Component.literal("\n")));
        basicHelpEntries.add(Component.translatable("commands.fpsm.help.basic.help").append(Component.literal("\n")));
        basicHelpEntries.add(Component.translatable("commands.fpsm.help.basic.save").append(Component.literal("\n")));
        basicHelpEntries.add(Component.translatable("commands.fpsm.help.basic.sync").append(Component.literal("\n")));
        basicHelpEntries.add(Component.translatable("commands.fpsm.help.basic.reload").append(Component.literal("\n")));
        helpEntries.put(HelpCategory.BASIC, basicHelpEntries);

        List<Component> listenerHelpEntries = new ArrayList<>();
        listenerHelpEntries.add(Component.translatable("commands.fpsm.help.category.listener").append(Component.literal("\n")));
        listenerHelpEntries.add(Component.translatable("commands.fpsm.help.listener.add_change_item").append(Component.literal("\n")));
        helpEntries.put(HelpCategory.LISTENER, listenerHelpEntries);

        List<Component> mapHelpEntries = new ArrayList<>();
        mapHelpEntries.add(Component.translatable("commands.fpsm.help.category.map").append(Component.literal("\n")));
        mapHelpEntries.add(Component.translatable("commands.fpsm.help.map.create").append(Component.literal("\n")));
        mapHelpEntries.add(Component.translatable("commands.fpsm.help.map.modify").append(Component.literal("\n")));
        mapHelpEntries.add(Component.translatable("commands.fpsm.help.map.debug").append(Component.literal("\n")));
        mapHelpEntries.add(Component.translatable("commands.fpsm.help.map.team_join_leave").append(Component.literal("\n")));
        mapHelpEntries.add(Component.translatable("commands.fpsm.help.map.team_players").append(Component.literal("\n")));
        helpEntries.put(HelpCategory.MAP, mapHelpEntries);

        return helpEntries;
    }

    private static int handleMapCapabilityHelp(CommandContext<CommandSourceStack> context) {
        return handleCapabilityHelp(context, true, 1);
    }

    private static int handleTeamCapabilityHelp(CommandContext<CommandSourceStack> context) {
        return handleCapabilityHelp(context, false, 1);
    }

    private static int handleCapabilityHelp(CommandContext<CommandSourceStack> context, boolean isMap, int page) {
        MutableComponent helpMessage = Component.translatable(isMap ? "commands.fpsm.help.capability.map.header" : "commands.fpsm.help.capability.team.header")
                .append(Component.literal("\n"));

        // 获取所有注册的能力
        Collection<Class<? extends FPSMCapability<?>>> capabilities = FPSMCapabilityManager.getRegisteredCapabilities(isMap ? FPSMCapabilityManager.CapabilityType.MAP : FPSMCapabilityManager.CapabilityType.TEAM);
        List<MutableComponent> allHelpEntries = new ArrayList<>();

        // 收集所有帮助信息
        for (Class<? extends FPSMCapability<?>> capClass : capabilities) {
            FPSMCapabilityManager.getRawFactory(capClass).ifPresent(factory -> {
                FPSMCapability.Factory.Command command = factory.command();
                if (command != null && !command.help().isEmpty()) {
                    // 添加能力名称
                    allHelpEntries.add(Component.literal(capClass.getSimpleName() + ":\n"));
                    for (MutableComponent help : command.help()) {
                        allHelpEntries.add(help.append(Component.literal("\n")));
                    }
                }
            });
        }

        // 计算分页
        int totalPages = (int) Math.ceil((double) allHelpEntries.size() / HELP_PER_PAGE);
        int startIndex = (page - 1) * HELP_PER_PAGE;
        int endIndex = Math.min(startIndex + HELP_PER_PAGE, allHelpEntries.size());

        // 添加当前页的帮助内容
        for (int i = startIndex; i < endIndex; i++) {
            helpMessage.append(allHelpEntries.get(i));
        }

        // 添加分页信息
        helpMessage.append(Component.translatable("commands.fpsm.help.page_info", page, totalPages))
                .append(Component.literal("\n"))
                .append(Component.translatable("commands.fpsm.help.footer"));

        context.getSource().sendSuccess(() -> helpMessage, false);
        return 1;
    }

    /**
     * 处理带有category的帮助请求
     */
    private static int handleHelpWithCategory(CommandContext<CommandSourceStack> context) {
        String categoryStr = StringArgumentType.getString(context, "category");
        HelpCategory category = parseHelpCategory(categoryStr);
        return handleHelpWithPageNumber(context, 1, category);
    }

    /**
     * 处理带有category和page的帮助请求
     */
    private static int handleHelpWithCategoryAndPage(CommandContext<CommandSourceStack> context) {
        String categoryStr = StringArgumentType.getString(context, "category");
        HelpCategory category = parseHelpCategory(categoryStr);
        int page = IntegerArgumentType.getInteger(context, "page");
        return handleHelpWithPageNumber(context, page, category);
    }

    /**
     * 将字符串转换为HelpCategory枚举
     */
    private static HelpCategory parseHelpCategory(String categoryStr) {
        try {
            return HelpCategory.valueOf(categoryStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    public static <T extends TeamCapability> Optional<T> getTeamCapability(CommandContext<CommandSourceStack> context , Class<T> clazz) {
        Optional<ServerTeam> team = FPSMCommand.getTeamByName(context);
        return team.flatMap(serverTeam -> serverTeam.getCapabilityMap().get(clazz));
    }

    public static <T extends MapCapability> Optional<T> getMapCapability(CommandContext<CommandSourceStack> context , Class<T> clazz) {
        Optional<BaseMap> map = FPSMCommand.getMapByName(context);
        return map.flatMap(baseMap -> baseMap.getCapabilityMap().get(clazz));
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