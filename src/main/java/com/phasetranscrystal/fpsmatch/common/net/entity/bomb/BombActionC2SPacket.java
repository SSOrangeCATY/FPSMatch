package com.phasetranscrystal.fpsmatch.common.net.entity.bomb;

import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BlastModeMap;
import com.phasetranscrystal.fpsmatch.common.entity.CompositionC4Entity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record BombActionC2SPacket(boolean action) {

    public static void encode(BombActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.action);
    }

    public static BombActionC2SPacket decode(FriendlyByteBuf buf) {
        return new BombActionC2SPacket(
                buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(sender);
        if (map == null || sender == null) {
            ctx.get().setPacketHandled(true);
            return;
        }
        BaseTeam team = map.getMapTeams().getTeamByPlayer(sender).orElse(null);
        if (team == null) {
            ctx.get().setPacketHandled(true);
            return;
        }

        ctx.get().enqueueWork(() -> {
            if (map instanceof BlastModeMap<?> blastModeMap && !blastModeMap.checkCanPlacingBombs(team.getFixedName())) {
                List<? extends CompositionC4Entity> entities = sender.serverLevel().getEntities(EntityTypeTest.forClass(CompositionC4Entity.class),(t)->{
                    LivingEntity player = t.getDemolisher();
                    return player != null && player.getUUID().equals(sender.getUUID());
                });
                if(!action){
                    entities.forEach(CompositionC4Entity::resetDemolisher);
                }else{
                    HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(sender,(entity -> entity instanceof CompositionC4Entity),2);
                    if(hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof CompositionC4Entity c4){
                        c4.setDemolisher(sender);
                    }else{
                        entities.forEach(CompositionC4Entity::resetDemolisher);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
