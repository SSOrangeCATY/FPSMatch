package com.phasetranscrystal.fpsmatch.common.packet;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public final class ClientPacketExecutor {
    private ClientPacketExecutor() {
    }

    public static void execute(Supplier<NetworkPacketRegister.Context> ctxSupplier, Object packet) {
        NetworkPacketRegister.Context context = ctxSupplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.getDist() == Dist.CLIENT) {
                ClientPacketRegistry.handle(packet);
            }
        });
        context.setPacketHandled(true);
    }
}
