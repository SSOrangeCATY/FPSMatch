package com.phasetranscrystal.fpsmatch.common.packet.spec;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

public class KillCamS2CPacket {

    private static final Logger LOG = LogUtils.getLogger();

    public final UUID      killerId;
    public final String    killerName;
    public final ItemStack weapon;
    public final Vec3      killerPos;  // B
    public final Vec3      victimPos;  // A

    public KillCamS2CPacket(UUID id, String name, ItemStack weapon,
                            double kx, double ky, double kz,
                            double vx, double vy, double vz) {
        this.killerId   = id;
        this.killerName = name;
        this.weapon     = weapon == null ? ItemStack.EMPTY : weapon;
        this.killerPos  = new Vec3(kx, ky, kz);
        this.victimPos  = new Vec3(vx, vy, vz);
    }

    public static void encode(KillCamS2CPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.killerId);
        buf.writeUtf(p.killerName);
        buf.writeItem(p.weapon);
        buf.writeDouble(p.killerPos.x).writeDouble(p.killerPos.y).writeDouble(p.killerPos.z);
        buf.writeDouble(p.victimPos.x).writeDouble(p.victimPos.y).writeDouble(p.victimPos.z);
    }

    public static KillCamS2CPacket decode(FriendlyByteBuf buf) {
        UUID   id   = buf.readUUID();
        String name = buf.readUtf();
        ItemStack weapon = buf.readItem();
        double kx = buf.readDouble(), ky = buf.readDouble(), kz = buf.readDouble();
        double vx = buf.readDouble(), vy = buf.readDouble(), vz = buf.readDouble();
        return new KillCamS2CPacket(id, name, weapon, kx, ky, kz, vx, vy, vz);
    }

    public static void handle(KillCamS2CPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LOG.info("[KillCamC] RECV packet  killer='{}'  A(victimEye)=({},{},{})  B(killerEye)=({},{},{})",
                    p.killerName,
                    fmt2(p.victimPos.x), fmt2(p.victimPos.y), fmt2(p.victimPos.z),
                    fmt2(p.killerPos.x), fmt2(p.killerPos.y), fmt2(p.killerPos.z));

            KillCamClientCache.cache(p.killerPos, p.victimPos, p.killerId, p.killerName, p.weapon);
            KillCamManager.startFromPacket();
        });
        ctx.get().setPacketHandled(true);
    }

    private static String fmt2(double v){ return String.format(java.util.Locale.ROOT, "%.2f", v); }
}