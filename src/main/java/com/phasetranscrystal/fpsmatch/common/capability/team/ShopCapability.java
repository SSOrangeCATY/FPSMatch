package com.phasetranscrystal.fpsmatch.common.capability.team;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommandSuggests;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.shop.functional.LMManager;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.item.IGun;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Optional;

/**
 * 商店能力：为队伍提供商店系统支持
 * 使用注册的商店类型系统，无需泛型
 */
public class ShopCapability extends TeamCapability implements FPSMCapability.Savable<FPSMShop<?>> {
    private final ServerTeam team;
    private FPSMShop<?> shop;
    private String shopTypeId;
    private int startMoney = 800;
    private boolean initialized = false;

    public ShopCapability(ServerTeam team) {
        this.team = team;
    }

    /**
     * 初始化商店系统
     */
    public boolean initialize(String shopTypeId, int startMoney) {
        try {
            this.shopTypeId = shopTypeId;
            this.startMoney = startMoney;
            this.shop = FPSMShop.createWithTypeId(shopTypeId, team.name, startMoney);
            this.initialized = true;

            FPSMatch.LOGGER.info("ShopCapability: Initialized shop for team {} with type {} and start money {}",
                    team.name, shopTypeId, startMoney);
            return true;
        } catch (Exception e) {
            FPSMatch.LOGGER.error("Failed to initialize shop for team {}: {}", team.name, e.getMessage());
            this.initialized = false;
            return false;
        }
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized && shop != null;
    }

    /**
     * 设置起始金钱（仅在使用前设置有效）
     */
    public void setStartMoney(int startMoney) {
        this.startMoney = startMoney;
        if (isInitialized()) {
            this.shop.setStartMoney(startMoney);
        }
    }

    /**
     * 获取商店实例
     */
    public FPSMShop<?> getShop() {
        if (!isInitialized()) {
            throw new IllegalStateException("ShopCapability not initialized. Call initialize() first.");
        }
        return shop;
    }

    /**
     * 安全获取商店实例
     */
    public Optional<FPSMShop<?>> getShopSafe() {
        return isInitialized() ? Optional.of(shop) : Optional.empty();
    }

    /**
     * 获取商店类型ID
     */
    public String getShopTypeId() {
        return shopTypeId;
    }

    /**
     * 获取起始金钱
     */
    public int getStartMoney() {
        return startMoney;
    }

    /**
     * 重置玩家商店数据
     */
    public void resetPlayerData() {
        if (isInitialized()) {
            shop.resetPlayerData();
        }
    }

    /**
     * 同步商店数据
     */
    public void syncShopData() {
        if (isInitialized()) {
            shop.syncShopData();
        }
    }

    /**
     * 同步金钱数据
     */
    public void syncShopMoneyData() {
        if (isInitialized()) {
            shop.syncShopMoneyData();
        }
    }

    @Override
    public BaseTeam getHolder() {
        return team;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Codec<FPSMShop<?>> codec() {
        if (!isInitialized()) {
            throw new IllegalStateException("ShopCapability not initialized. Cannot get codec.");
        }
        // 这里需要根据实际的商店类型获取对应的codec
        return (Codec<FPSMShop<?>>) (Codec<?>) shop.getCodec();
    }

    @Override
    public FPSMShop<?> write(FPSMShop<?> value) {
        if (isInitialized()) {
            this.shop = value;
        }
        return this.shop;
    }

    @Override
    public FPSMShop<?> read() {
        return getShop();
    }

    @Override
    public void destroy() {
        if (isInitialized()) {
            shop.clearPlayerShopData();
            initialized = false;
            shop = null;
            shopTypeId = null;
        }
    }

    @Override
    public void reset() {
        if (isInitialized()) {
            resetPlayerData();
        }
    }

    /**
     * 注册能力到全局管理器
     */
    public static void register() {
        FPSMCapabilityManager.register(ShopCapability.class, new Factory<BaseTeam, ShopCapability>() {
            @Override
            public ShopCapability create(BaseTeam team) {
                if (team instanceof ServerTeam serverTeam) {
                    return new ShopCapability(serverTeam);
                } else {
                    throw new IllegalArgumentException("Team is client side");
                }
            }

            @Override
            public Command command() {
                return new ShopCommand();
            }
        });
    }

    /**
     * 商店命令处理器
     */
    protected static class ShopCommand implements Factory.Command {
        @Override
        public String getName() {
            return "shop";
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> builder(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
            return builder
                    .then(Commands.literal("initialize")
                            .then(Commands.argument("type", StringArgumentType.string())
                                    .suggests(FPSMCommandSuggests.SHOP_TYPE_SUGGESTION)
                                    .executes(c -> handleInitialize(c, 800))
                                    .then(Commands.argument("startMoney", IntegerArgumentType.integer(0))
                                            .executes(c -> handleInitialize(c, IntegerArgumentType.getInteger(c, "startMoney"))))))
                    .then(Commands.literal("reset")
                            .executes(ShopCommand::handleReset))
                    .then(Commands.literal("sync")
                            .executes(ShopCommand::handleSync))
                    .then(Commands.literal("info")
                            .executes(ShopCommand::handleInfo))
                    .then(Commands.literal("modify")
                            .then(Commands.literal("set")
                                    .then(Commands.argument(FPSMCommandSuggests.SHOP_NAME_ARG, StringArgumentType.string())
                                            .suggests(FPSMCommandSuggests.SHOP_NAMES_SUGGESTION)
                                            .then(Commands.argument(FPSMCommandSuggests.SHOP_TYPE_ARG, StringArgumentType.string())
                                                    .suggests(FPSMCommandSuggests.SHOP_ITEM_TYPES_SUGGESTION)
                                                    .then(Commands.argument(FPSMCommandSuggests.SHOP_SLOT_ARG, IntegerArgumentType.integer(1, 5))
                                                            .suggests(FPSMCommandSuggests.SHOP_SET_SLOT_ACTION_SUGGESTION)
                                                            .then(Commands.literal("listener_module")
                                                                    .then(Commands.literal("add")
                                                                            .then(Commands.argument("listener_module", StringArgumentType.string())
                                                                                    .suggests(FPSMCommandSuggests.SHOP_SLOT_ADD_LISTENER_MODULES_SUGGESTION)
                                                                                    .executes(ShopCommand::handleAddListenerModule)))
                                                                    .then(Commands.literal("remove")
                                                                            .then(Commands.argument("listener_module", StringArgumentType.string())
                                                                                    .suggests(FPSMCommandSuggests.SHOP_SLOT_REMOVE_LISTENER_MODULES_SUGGESTION)
                                                                                    .executes(ShopCommand::handleRemoveListenerModule))))
                                                            .then(Commands.literal("group_id")
                                                                    .then(Commands.argument("group_id", IntegerArgumentType.integer(0))
                                                                            .executes(ShopCommand::handleModifyShopGroupID)))
                                                            .then(Commands.literal("cost")
                                                                    .then(Commands.argument("cost", IntegerArgumentType.integer(0))
                                                                            .executes(ShopCommand::handleModifyCost)))
                                                            .then(Commands.literal("item")
                                                                    .executes(ShopCommand::handleModifyItemWithoutValue)
                                                                    .then(Commands.argument("item", ItemArgument.item(context))
                                                                            .executes(ShopCommand::handleModifyItem)))
                                                            .then(Commands.literal("dummy_ammo_amount")
                                                                    .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                                            .executes(ShopCommand::handleGunModifyGunAmmoAmount))))))));
        }

        /**
         * 处理商店初始化
         */
        private static int handleInitialize(CommandContext<CommandSourceStack> context, int startMoney) {
            String typeId = StringArgumentType.getString(context, "type");

            return FPSMCommand.getTeamCapability(context, ShopCapability.class).map(capability -> {
                if (!FPSMShop.isShopTypeRegistered(typeId)) {
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.shop.initialize.type_not_found", typeId));
                    return 0;
                }

                if (capability.initialize(typeId, startMoney)) {
                    context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.shop.initialize.success",
                            capability.team.name, typeId, startMoney), true);
                    return 1;
                } else {
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.shop.initialize.failed", typeId));
                    return 0;
                }
            }).orElse(0);
        }

        /**
         * 处理重置玩家数据
         */
        private static int handleReset(CommandContext<CommandSourceStack> context) {
            return FPSMCommand.getTeamCapability(context, ShopCapability.class).map(capability -> {
                if (!capability.isInitialized()) {
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.shop.not_initialized"));
                    return 0;
                }

                capability.resetPlayerData();
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.shop.reset.success",
                        capability.team.name), true);
                return 1;
            }).orElse(0);
        }

        /**
         * 处理数据同步
         */
        private static int handleSync(CommandContext<CommandSourceStack> context) {
            return FPSMCommand.getTeamCapability(context, ShopCapability.class).map(capability -> {
                if (!capability.isInitialized()) {
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.shop.not_initialized"));
                    return 0;
                }

                capability.syncShopData();
                capability.syncShopMoneyData();
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.shop.sync.success",
                        capability.team.name), true);
                return 1;
            }).orElse(0);
        }

        /**
         * 处理信息查询
         */
        private static int handleInfo(CommandContext<CommandSourceStack> context) {
            return FPSMCommand.getTeamCapability(context, ShopCapability.class).map(capability -> {
                if (!capability.isInitialized()) {
                    context.getSource().sendFailure(Component.translatable("commands.fpsm.modify.shop.not_initialized"));
                    return 0;
                }

                FPSMShop<?> shop = capability.getShop();
                context.getSource().sendSuccess(() -> Component.translatable("commands.fpsm.modify.shop.info",
                        capability.team.name,
                        capability.getShopTypeId(),
                        capability.getStartMoney(),
                        shop.playersData.size()), true);
                return 1;
            }).orElse(0);
        }

        // ------------------------------ 商店相关处理方法 ------------------------------
        private static int handleAddListenerModule(CommandContext<CommandSourceStack> context) {
            String moduleName = StringArgumentType.getString(context, "listener_module");
            String shopName = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_NAME_ARG);
            String shopType = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
            int slotNum = IntegerArgumentType.getInteger(context, FPSMCommandSuggests.SHOP_SLOT_ARG) - 1;

            LMManager manager = FPSMCore.getInstance().getListenerModuleManager();

            return FPSMCommand.getMapByName(context)
                    .flatMap(map -> FPSMCommand.getShop(context, map, shopName))
                    .map(shop -> {
                        ListenerModule module = manager.getListenerModule(moduleName);
                        shop.addDefaultShopDataListenerModule(shopType, slotNum, module);
                        FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.listener.add.success", moduleName));
                        return 1;
                    })
                    .orElseGet(() -> {
                        String mapName = StringArgumentType.getString(context, FPSMCommandSuggests.MAP_NAME_ARG);
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                        return 0;
                    });
        }

        private static int handleRemoveListenerModule(CommandContext<CommandSourceStack> context) {
            String moduleName = StringArgumentType.getString(context, "listener_module");
            String shopName = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_NAME_ARG);
            String shopType = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
            int slotNum = IntegerArgumentType.getInteger(context, FPSMCommandSuggests.SHOP_SLOT_ARG) - 1;

            return FPSMCommand.getMapByName(context)
                    .flatMap(map -> FPSMCommand.getShop(context, map, shopName))
                    .map(shop -> {
                        shop.removeDefaultShopDataListenerModule(shopType, slotNum, moduleName);
                        FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.slot.listener.remove.success", moduleName));
                        return 1;
                    })
                    .orElseGet(() -> {
                        String mapName = StringArgumentType.getString(context, FPSMCommandSuggests.MAP_NAME_ARG);
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                        return 0;
                    });
        }

        private static int handleModifyShopGroupID(CommandContext<CommandSourceStack> context) {
            int group_id = IntegerArgumentType.getInteger(context, "group_id");
            String shopName = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_NAME_ARG);
            String shopType = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
            int slotNum = IntegerArgumentType.getInteger(context, FPSMCommandSuggests.SHOP_SLOT_ARG) - 1;

            return FPSMCommand.getMapByName(context)
                    .flatMap(map -> FPSMCommand.getShop(context, map, shopName))
                    .map(shop -> {
                        shop.setDefaultShopDataGroupId(shopType, slotNum, group_id);
                        FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.group.success", shopType, slotNum, group_id));
                        return 1;
                    })
                    .orElseGet(() -> {
                        String mapName = StringArgumentType.getString(context, FPSMCommandSuggests.MAP_NAME_ARG);
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                        return 0;
                    });
        }

        private static int handleModifyCost(CommandContext<CommandSourceStack> context) {
            String shopName = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_NAME_ARG);
            String shopType = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
            int slotNum = IntegerArgumentType.getInteger(context, FPSMCommandSuggests.SHOP_SLOT_ARG) - 1;
            int cost = IntegerArgumentType.getInteger(context, "cost");

            return FPSMCommand.getMapByName(context)
                    .flatMap(map -> FPSMCommand.getShop(context, map, shopName))
                    .map(shop -> {
                        shop.setDefaultShopDataCost(shopType, slotNum, cost);
                        FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.modify.cost.success", shopType, slotNum, cost));
                        return 1;
                    })
                    .orElseGet(() -> {
                        String mapName = StringArgumentType.getString(context, FPSMCommandSuggests.MAP_NAME_ARG);
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                        return 0;
                    });
        }

        private static int handleModifyItemWithoutValue(CommandContext<CommandSourceStack> context) {
            try {
                ServerPlayer player = FPSMCommand.getPlayerOrFail(context);
                String shopName = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_NAME_ARG);
                String shopType = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
                int slotNum = IntegerArgumentType.getInteger(context, FPSMCommandSuggests.SHOP_SLOT_ARG) - 1;

                return FPSMCommand.getMapByName(context)
                        .flatMap(map -> FPSMCommand.getShop(context, map, shopName))
                        .map(shop -> {
                            ItemStack itemStack = player.getMainHandItem().copy();
                            if (itemStack.getItem() instanceof IGun iGun) {
                                FPSMUtil.fixGunItem(itemStack, iGun);
                            }
                            shop.setDefaultShopDataItemStack(shopType, slotNum, itemStack);
                            FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.modify.item.success",
                                    shopType, slotNum, itemStack.getDisplayName()));
                            return 1;
                        })
                        .orElseGet(() -> {
                            String mapName = StringArgumentType.getString(context, FPSMCommandSuggests.MAP_NAME_ARG);
                            FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                            return 0;
                        });
            } catch (CommandSyntaxException e) {
                return 0;
            }
        }

        private static int handleModifyItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
            String shopName = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_NAME_ARG);
            String shopType = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
            int slotNum = IntegerArgumentType.getInteger(context, FPSMCommandSuggests.SHOP_SLOT_ARG) - 1;

            ItemStack itemStack = ItemArgument.getItem(context, "item").createItemStack(1, false);

            return FPSMCommand.getMapByName(context)
                    .flatMap(map -> FPSMCommand.getShop(context, map, shopName))
                    .map(shop -> {
                        if (itemStack.getItem() instanceof IGun iGun) {
                            FPSMUtil.fixGunItem(itemStack, iGun);
                        }
                        shop.setDefaultShopDataItemStack(shopType, slotNum, itemStack);
                        FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.modify.item.success",
                                shopType, slotNum, itemStack.getDisplayName()));
                        return 1;
                    })
                    .orElseGet(() -> {
                        String mapName = StringArgumentType.getString(context, FPSMCommandSuggests.MAP_NAME_ARG);
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                        return 0;
                    });
        }

        private static int handleGunModifyGunAmmoAmount(CommandContext<CommandSourceStack> context) {
            String shopName = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_NAME_ARG);
            String shopType = StringArgumentType.getString(context, FPSMCommandSuggests.SHOP_TYPE_ARG).toUpperCase(Locale.ROOT);
            int slotNum = IntegerArgumentType.getInteger(context, FPSMCommandSuggests.SHOP_SLOT_ARG) - 1;
            int amount = IntegerArgumentType.getInteger(context, "amount");

            return FPSMCommand.getMapByName(context)
                    .flatMap(map -> FPSMCommand.getShop(context, map, shopName))
                    .map(shop -> {
                        ItemStack itemStack = shop.getDefaultShopDataItemStack(shopType, slotNum);
                        if (itemStack.getItem() instanceof IGun iGun) {
                            FPSMUtil.setDummyAmmo(itemStack, iGun, amount);
                        }
                        shop.setDefaultShopDataItemStack(shopType, slotNum, itemStack);
                        FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.shop.modify.gun.success",
                                shopType, slotNum, itemStack.getDisplayName(), amount));
                        return 1;
                    })
                    .orElseGet(() -> {
                        String mapName = StringArgumentType.getString(context, FPSMCommandSuggests.MAP_NAME_ARG);
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.shop.slot.modify.fail", shopName, mapName));
                        return 0;
                    });
        }
    }
}