package com.phasetranscrystal.fpsmatch.common.capability.map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommand;
import com.phasetranscrystal.fpsmatch.common.command.FPSMCommandSuggests;
import com.phasetranscrystal.fpsmatch.common.command.FPSMHelpManager;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class GameEndTeleportCapability extends MapCapability implements FPSMCapability.Savable<SpawnPointData> {

    public static void register() {
        FPSMCapabilityManager.register(FPSMCapabilityManager.CapabilityType.MAP, GameEndTeleportCapability.class, new Factory<>() {
            @Override
            public GameEndTeleportCapability create(BaseMap map) {
                return new GameEndTeleportCapability(map);
            }

            @Override
            public Command command(){
                return new METPCommand();
            }
        });
    }

    private final BaseMap map;

    private SpawnPointData pos;

    private GameEndTeleportCapability(BaseMap map) {
        this.map = map;
    }

    public void setPoint(SpawnPointData data) {
        pos = data;
    }

    public SpawnPointData getPoint() {
        return pos;
    }

    @Override
    public BaseMap getHolder() {
        return map;
    }

    @Override
    public Codec<SpawnPointData> codec() {
        return SpawnPointData.CODEC;
    }

    @Override
    public SpawnPointData write(SpawnPointData value) {
        this.pos = value;
        return this.pos;
    }

    @Override
    public SpawnPointData read() {
        return pos;
    }

    public static class METPCommand implements FPSMCapability.Factory.Command{

        @Override
        public String getName() {
            return "match_end_teleport_point";
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> builder(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context) {
            return builder
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                    .executes(METPCommand::handleModifyMatchEndTeleportPoint));
        }

        @Override
        public void help(FPSMHelpManager helper) {
            helper.registerCommandHelp(FPSMHelpManager.withMapCapability("match_end_teleport_point"));
            helper.registerCommandHelp(FPSMHelpManager.withMapCapability("match_end_teleport_point pos"), Component.translatable("commands.fpsm.help.capability.match_end_teleport_point.set"));
            helper.registerCommandParameters(FPSMHelpManager.withMapCapability("match_end_teleport_point pos"), "*point");
        }

        private static int handleModifyMatchEndTeleportPoint(CommandContext<CommandSourceStack> context) {
            Vec3 point = Vec3Argument.getVec3(context, "pos");

            return FPSMCommand.getMapCapability(context, GameEndTeleportCapability.class).map(
                    cap->{
                        SpawnPointData pointData = new SpawnPointData(
                                context.getSource().getLevel().dimension(),
                                point, 0f, 0f
                        );
                        cap.setPoint(pointData);
                        FPSMCommand.sendSuccess(context.getSource(), Component.translatable("commands.fpsm.modify.metp.success", pointData.toString()));
                        return 1;
                    }).orElseGet(() -> {
                        FPSMCommand.sendFailure(context.getSource(), Component.translatable("commands.fpsm.capability.missing", GameEndTeleportCapability.class.getSimpleName()));
                        return 0;
                    });
        }
    }
}
