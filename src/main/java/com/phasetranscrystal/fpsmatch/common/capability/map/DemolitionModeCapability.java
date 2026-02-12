package com.phasetranscrystal.fpsmatch.common.capability.map;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.common.command.FPSMHelpManager;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.entity.BlastBombEntity;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BlastBombState;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DemolitionModeCapability extends MapCapability implements FPSMCapability.Savable<DemolitionModeCapability.Data> {

    public static void register() {
        FPSMCapabilityManager.register(FPSMCapabilityManager.CapabilityType.MAP, DemolitionModeCapability.class, new Factory<>() {
            @Override
            public DemolitionModeCapability create(BaseMap map) {
                return new DemolitionModeCapability(map);
            }

            @Override
            public Command command(){
                return new DemolitionCommand();
            }
        });
    }

    private final Data data = new Data();

    private BlastBombEntity c4;

    private DemolitionModeCapability(BaseMap map) {
        super(map);
    }

    /**
     * 添加一个炸弹区域到地图。
     * <p>
     * 炸弹区域用于定义可以放置炸弹的区域范围。
     *
     * @param area 炸弹区域数据
     */
    public void addBombArea(AreaData area) {
        this.data.getBombAreaData().add(area);
    }

    /**
     * 获取所有炸弹区域的数据。
     * <p>
     * 返回一个包含所有炸弹区域的列表。
     *
     * @return 炸弹区域数据列表
     */
    public List<AreaData> getBombAreaData() {
        return this.data.getBombAreaData();
    }

    /**
     * 设置爆破方的队伍名称。
     * <p>
     * 爆破方是指在游戏中负责放置炸弹的队伍。
     *
     * @param team 爆破方队伍名称
     */
    public void setDemolitionTeam(ServerTeam team) {
        this.data.setDemolitionTeam(team.getFixedName());
    }

    /**
     * 设置当前的爆破状态。
     * <p>
     * 爆破状态用于表示炸弹是否正在爆破过程中。
     *
     * @param bomb 炸弹实体
     */
    public void setBombEntity(@Nullable BlastBombEntity bomb) {
        if (bomb == null) {
            if (this.c4 != null && !this.c4.isRemoved()) {
                this.c4.discard();
            }
            this.c4 = null;
        } else {
            this.c4 = bomb;
        }
    }

    /**
     * 获取当前的爆破状态。
     * <p>
     * 返回爆破状态的时间倒计时，如果爆破未开始则返回 0。
     *
     * @return 爆破状态
     */
    public BlastBombState blastState() {
        return this.c4 == null ? BlastBombState.NONE : this.c4.getState();
    }

    /**
     * 检查指定队伍是否可以放置炸弹。
     * <p>
     * 该方法用于判断当前队伍是否有权限放置炸弹。
     *
     * @param team 队伍完整名称
     * @return 如果可以放置炸弹，返回 true；否则返回 false
     */
    public boolean checkCanPlacingBombs(String team) {
        if (this.data.getDemolitionTeam() == null) return false;
        return this.data.getDemolitionTeam().equals(team);
    }

    public String getDemolitionTeam() {
        return data.getDemolitionTeam();
    }

    /**
     * 检查玩家是否在炸弹安放区域
     *
     * @param player 目标玩家
     * @return 是否在有效炸弹区域
     * @see AreaData 区域检测逻辑
     */
    public boolean checkPlayerIsInBombArea(Player player) {
        for (AreaData area : data.getBombAreaData()) {
            if (area.isPlayerInArea(player)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Codec<Data> codec() {
        return Data.CODEC;
    }

    @Override
    public Data write(Data value) {
        this.data.bombAreas.clear();
        this.data.bombAreas.addAll(value.bombAreas);
        this.data.demolitionTeam = value.demolitionTeam;
        return this.data;
    }

    @Override
    public Data read() {
        return data;
    }

    public static class Data {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                // 炸弹区域数据
                AreaData.CODEC.listOf().fieldOf("bombAreas")
                        .forGetter(Data::getBombAreaData),

                // 爆破队伍
                Codec.STRING.fieldOf("blastTeam")
                        .forGetter(Data::getDemolitionTeam)
        ).apply(instance, Data::new));

        private final List<AreaData> bombAreas;

        private String demolitionTeam;

        public Data() {
            bombAreas = new ArrayList<>();
            demolitionTeam = "";
        }

        public Data(List<AreaData> areas, String demolitionTeam) {
            this.bombAreas = new ArrayList<>();
            this.bombAreas.addAll(areas);
            this.demolitionTeam = demolitionTeam;
        }

        public void setDemolitionTeam(String demolitionTeam) {
            this.demolitionTeam = demolitionTeam;
        }

        public List<AreaData> getBombAreaData() {
            return bombAreas;
        }

        public String getDemolitionTeam() {
            return demolitionTeam;
        }
    }

    public static class DemolitionCommand implements Factory.Command {
        @Override
        public String getName() {
            return "demolition";
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> builder(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
            return builder
                    .then(Commands.literal("bomb_area")
                            .then(Commands.literal("add")
                                    .then(Commands.argument("from", BlockPosArgument.blockPos())
                                            .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                    .executes(DemolitionCommand::handleBombAreaAction)))));
        }

        @Override
        public void help(FPSMHelpManager helper) {
            helper.registerCommandHelp(FPSMHelpManager.withMapCapability("demolition"));
            helper.registerCommandHelp(FPSMHelpManager.withMapCapability("demolition bomb_area"));
            helper.registerCommandHelp(FPSMHelpManager.withMapCapability("demolition bomb_area add"), Component.translatable("commands.fpsm.help.capability.demolition.bomb_area.add"));
            helper.registerCommandParameters(FPSMHelpManager.withMapCapability("demolition bomb_area add"), "*from", "*to");
        }

        private static int handleBombAreaAction(CommandContext<CommandSourceStack> context) {
            BlockPos pos1 = BlockPosArgument.getBlockPos(context, "from");
            BlockPos pos2 = BlockPosArgument.getBlockPos(context, "to");

            return FPSMCommand.getMapCapability(context, DemolitionModeCapability.class)
                    .map(cap -> {
                        cap.addBombArea(new AreaData(pos1, pos2));
                        FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.bombarea.success"));
                        return 1;
                    })
                    .orElseGet(() -> {
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.capability.missing", DemolitionModeCapability.class.getSimpleName()));
                        return 0;
                    });
        }
    }
}
