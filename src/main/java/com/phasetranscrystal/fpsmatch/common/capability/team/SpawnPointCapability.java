package com.phasetranscrystal.fpsmatch.common.capability.team;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommandSuggests;
import com.phasetranscrystal.fpsmatch.common.command.FPSMHelpManager;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.*;


public class SpawnPointCapability extends TeamCapability implements FPSMCapability.Savable<List<SpawnPointData>> {
    public final BaseTeam team;
    private final List<SpawnPointData> spawnPointsData = new ArrayList<>();

    private SpawnPointCapability(BaseTeam team) {
        this.team = team;
    }
    /**
     * 注册能力到全局管理器
     */
    public static void register() {
        FPSMCapabilityManager.register(FPSMCapabilityManager.CapabilityType.TEAM, SpawnPointCapability.class, new TeamCapability.Factory<>() {
            @Override
            public boolean isOriginal() {
                return true;
            }

            @Override
            public SpawnPointCapability create(BaseTeam team) {
                return new SpawnPointCapability(team);
            }

            @Override
            public Command command(){
                return new SpawnPointCommand();
            }
        });
    }


    public void setAllSpawnPointData(List<SpawnPointData> spawnPointsData) {
        this.spawnPointsData.clear();
        this.spawnPointsData.addAll(spawnPointsData);
    }

    public boolean randomSpawnPoints() {
        if(team.isSpectator()) return true;

        Random random = new Random();

        if (this.spawnPointsData.isEmpty()) {
            FPSMCore.getInstance().getServer().sendSystemMessage(Component.translatable("message.fpsmatch.error.no_spawn_points")
                    .append(Component.literal("error from -> " + team.name)).withStyle(ChatFormatting.RED));
            return false;
        }

        Map<UUID, PlayerData> players = this.team.getPlayers();
        if (this.spawnPointsData.size() < players.size()) {
            FPSMCore.getInstance().getServer().sendSystemMessage(Component.translatable("message.fpsmatch.error.not_enough_spawn_points")
                    .append(Component.literal("error from -> " + team.name)).withStyle(ChatFormatting.RED));
            return false;
        }

        List<UUID> playerUUIDs = new ArrayList<>(players.keySet());
        List<SpawnPointData> list = new ArrayList<>(this.spawnPointsData);
        for (UUID playerUUID : playerUUIDs) {
            if (list.isEmpty()) {
                list.addAll(this.spawnPointsData);
            }
            int randomIndex = random.nextInt(list.size());
            SpawnPointData spawnPoint = list.get(randomIndex);
            list.remove(randomIndex);
            players.get(playerUUID).setSpawnPointsData(spawnPoint);
        }
        return true;
    }

    public void addSpawnPointData(@Nonnull SpawnPointData data) {
        this.spawnPointsData.add(data);
    }

    public void addAllSpawnPointData(@Nonnull List<SpawnPointData> data) {
        this.spawnPointsData.addAll(data);
    }

    public List<SpawnPointData> getSpawnPointsData() {
        return spawnPointsData;
    }

    public void clearSpawnPointsData() {
        spawnPointsData.clear();
    }

    @Override
    public void reset() {
        randomSpawnPoints();
    }

    @Override
    public void destroy() {
        clearSpawnPointsData();
    }

    @Override
    public BaseTeam getHolder() {
        return team;
    }

    @Override
    public boolean isImmutable(){
        return true;
    }

    @Override
    public Codec<List<SpawnPointData>> codec() {
        return SpawnPointData.CODEC.listOf();
    }

    @Override
    public List<SpawnPointData> write(List<SpawnPointData> value) {
        spawnPointsData.clear();
        spawnPointsData.addAll(value);
        return spawnPointsData;
    }

    @Override
    public @Nullable List<SpawnPointData> read() {
        return spawnPointsData;
    }

    static class SpawnPointCommand implements TeamCapability.Factory.Command {
        @Override
        public String getName() {
            return "spawnpoints";
        }

        // 构建指令树，挂载到团队指令后
        @Override
        public LiteralArgumentBuilder<CommandSourceStack> builder(LiteralArgumentBuilder<CommandSourceStack> parent, CommandBuildContext context) {
            return parent
                    .then(Commands.literal("add")
                            .executes(SpawnPointCommand::handleSpawnAdd))
                    .then(Commands.literal("clear")
                            .executes(SpawnPointCommand::handleSpawnClear))
                    .then(Commands.literal("clear_all")
                            .executes(SpawnPointCommand::handleSpawnClearAll))
                    .then(Commands.literal("set")
                            .then(Commands.argument("from", Vec2Argument.vec2())
                                    .then(Commands.argument("to", Vec2Argument.vec2())
                                            .executes(SpawnPointCommand::handleSpawnSet))));
        }

        @Override
        public void help(FPSMHelpManager helper) {
            helper.registerCommandHelp(FPSMHelpManager.withTeamCapability("spawnpoints"));
            helper.registerCommandHelp(FPSMHelpManager.withTeamCapability("spawnpoints add"), Component.translatable("commands.fpsm.help.capability.spawnpoints.add"));
            helper.registerCommandHelp(FPSMHelpManager.withTeamCapability("spawnpoints clear"), Component.translatable("commands.fpsm.help.capability.spawnpoints.clear"));
            helper.registerCommandHelp(FPSMHelpManager.withTeamCapability("spawnpoints clear_all"), Component.translatable("commands.fpsm.help.capability.spawnpoints.clear_all"));
            helper.registerCommandHelp(FPSMHelpManager.withTeamCapability("spawnpoints set"), Component.translatable("commands.fpsm.help.capability.spawnpoints.set"));
            
            // 注册命令参数
            helper.registerCommandParameters(FPSMHelpManager.withTeamCapability("spawnpoints set"), "*from", "*to");
        }

        private static int handleSpawnAdd(CommandContext<CommandSourceStack> context) {
            String teamName = StringArgumentType.getString(context, FPSMCommandSuggests.TEAM_NAME_ARG);
            return FPSMCommand.getTeamCapability(context, SpawnPointCapability.class).map(spawnCap -> {
                spawnCap.addSpawnPointData(getSpawnPointData(context));
                FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.add.success", teamName));
                return 1;
            }).orElseGet(() -> {
                FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.capability.missing","SpawnPointCapability"));
                return 0;
            });
        }

        private static int handleSpawnClear(CommandContext<CommandSourceStack> context) {
            String teamName = StringArgumentType.getString(context, FPSMCommandSuggests.TEAM_NAME_ARG);
            
            return FPSMCommand.getTeamCapability(context, SpawnPointCapability.class).map(spawnCap -> {
                        spawnCap.clearSpawnPointsData();
                        FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.clear.success", teamName));
                        return 1;
                    })
                    .orElseGet(() -> {
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.capability.missing","SpawnPointCapability"));
                        return 0;
                    });
        }

        private static int handleSpawnClearAll(CommandContext<CommandSourceStack> context) {
            return FPSMCommand.getTeamCapability(context, SpawnPointCapability.class).map(spawnCap -> {
                        spawnCap.clearSpawnPointsData();
                        FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.clearall.success"));
                        return 1;
                    })
                    .orElseGet(() -> {
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.team.notFound"));
                        return 0;
                    });
        }

        private static int handleSpawnSet(CommandContext<CommandSourceStack> context) {
            String teamName = StringArgumentType.getString(context, FPSMCommandSuggests.TEAM_NAME_ARG);
            
            return  FPSMCommand.getTeamCapability(context, SpawnPointCapability.class).map(spawnCap -> {
                        try {
                            Vec2 from = Vec2Argument.getVec2(context, "from");
                            Vec2 to = Vec2Argument.getVec2(context, "to");

                            int minX = (int) Math.floor(Math.min(from.x, to.x));
                            int maxX = (int) Math.floor(Math.max(from.x, to.x));
                            int minZ = (int) Math.floor(Math.min(from.y, to.y));
                            int maxZ = (int) Math.floor(Math.max(from.y, to.y));
                            int y = BlockPos.containing(context.getSource().getPosition()).getY();

                            double border = from.distanceToSqr(to);
                            if (border >= 130) {
                                FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.set.over_flow"));
                                return 0;
                            }

                            List<SpawnPointData> newSpawnPoints = new ArrayList<>();
                            SpawnPointData defaultData = getSpawnPointData(context);

                            for (int x = minX; x <= maxX; x++) {
                                for (int z = minZ; z <= maxZ; z++) {
                                    BlockPos pos = new BlockPos(x, y, z);
                                    BlockState block = context.getSource().getLevel().getBlockState(pos);
                                    if (block.isAir()) {
                                        newSpawnPoints.add(new SpawnPointData(
                                                defaultData.getDimension(), pos.getCenter().add(0,0.5,0),
                                                defaultData.getYaw(), defaultData.getPitch()
                                        ));
                                    }
                                }
                            }

                            spawnCap.setAllSpawnPointData(newSpawnPoints);
                            generateCubeEdgesParticles(context.getSource(), minX, y, minZ, maxX + 1, y + 1, maxZ + 1);
                            FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.set.success", newSpawnPoints.size(), teamName));
                            return 1;

                        } catch (IllegalArgumentException e) {
                            FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.modify.spawn.set.missing_args"));
                            return 0;
                        }
                    })
                    .orElseGet(() -> {
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.capability.missing","SpawnPointCapability"));
                        return 0;
                    });
        }


        private static void generateCubeEdgesParticles(CommandSourceStack source, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            BlockPos[][] edges = {
                    {new BlockPos(minX, minY, minZ), new BlockPos(maxX, minY, minZ)},
                    {new BlockPos(minX, minY, minZ), new BlockPos(minX, maxY, minZ)},
                    {new BlockPos(maxX, minY, minZ), new BlockPos(maxX, maxY, minZ)},
                    {new BlockPos(minX, maxY, minZ), new BlockPos(maxX, maxY, minZ)},
                    {new BlockPos(minX, minY, maxZ), new BlockPos(maxX, minY, maxZ)},
                    {new BlockPos(minX, minY, maxZ), new BlockPos(minX, maxY, maxZ)},
                    {new BlockPos(maxX, minY, maxZ), new BlockPos(maxX, maxY, maxZ)},
                    {new BlockPos(minX, maxY, maxZ), new BlockPos(maxX, maxY, maxZ)},
                    {new BlockPos(minX, minY, minZ), new BlockPos(minX, minY, maxZ)},
                    {new BlockPos(maxX, minY, minZ), new BlockPos(maxX, minY, maxZ)},
                    {new BlockPos(minX, maxY, minZ), new BlockPos(minX, maxY, maxZ)},
                    {new BlockPos(maxX, maxY, minZ), new BlockPos(maxX, maxY, maxZ)}
            };

            Arrays.stream(edges).forEach(edge ->
                    spawnParticlesAlongEdge(source, edge[0], edge[1])
            );
        }

        private static void spawnParticlesAlongEdge(CommandSourceStack source, BlockPos start, BlockPos end) {
            double x1 = start.getX(), y1 = start.getY(), z1 = start.getZ();
            double x2 = end.getX(), y2 = end.getY(), z2 = end.getZ();

            double dx = x2 - x1;
            double dy = y2 - y1;
            double dz = z2 - z1;

            for (double t = 0; t <= 1; t += 0.1) {
                double x = x1 + dx * t;
                double y = y1 + dy * t;
                double z = z1 + dz * t;
                if (source.getPlayer() != null) {
                    source.getLevel().sendParticles(source.getPlayer(), ParticleTypes.FLAME, false,
                            x, y, z, 0, dx, dy, dz, 0.0001);
                }
            }
        }

        private static SpawnPointData getSpawnPointData(CommandContext<CommandSourceStack> context) {
            Entity entity = context.getSource().getEntity();

            if (entity != null) {
                return new SpawnPointData(
                        context.getSource().getLevel().dimension(),
                        context.getSource().getPosition(), entity.getXRot(), entity.getYRot()
                );
            } else {
                return new SpawnPointData(
                        context.getSource().getLevel().dimension(),
                        context.getSource().getPosition(), 0f, 0f
                );
            }
        }
    }
}