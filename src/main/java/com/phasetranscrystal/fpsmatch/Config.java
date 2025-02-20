package com.phasetranscrystal.fpsmatch;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;

public class Config {
    public static class Client{
        public final ForgeConfigSpec.BooleanValue showLogin;
        public final ForgeConfigSpec.BooleanValue hudEnabled;
        public final ForgeConfigSpec.IntValue hudPosition;
        public final ForgeConfigSpec.IntValue messageShowTime;
        public final ForgeConfigSpec.IntValue maxShowCount;

        private Client(ForgeConfigSpec.Builder builder) {
            builder.push("client");
            {
                hudEnabled = builder.comment("Kill message enabled").define("hudEnabled",true);
                hudPosition = builder.comment("Kill message position").defineInRange("hudPosition",2,1,4);
                messageShowTime = builder.comment("Per message show time").defineInRange("messageShowTime",5,1,60);
                maxShowCount = builder.comment("Max show count").defineInRange("maxShowCount",5,1,10);
                showLogin = builder.comment("Show Login Message").define("showLoginMessage",true);
            }
            builder.pop();
        }
    }


    public static class Server{
        public final ForgeConfigSpec.BooleanValue dummy;
        private Server(ForgeConfigSpec.Builder builder) {
            builder.push("server");
            {
                dummy = builder.comment("dummy").define("dummy", false);
            }
            builder.pop();
        }
    }
    public static final Client client;
    public static final ForgeConfigSpec clientSpec;
    public static final Server server;
    public static final ForgeConfigSpec serverSpec;

    static {
        final Pair<Client, ForgeConfigSpec> clientSpecPair = new ForgeConfigSpec.Builder().configure(Client::new);
        client = clientSpecPair.getLeft();
        clientSpec = clientSpecPair.getRight();
        final Pair<Server,ForgeConfigSpec> serverSpecPair = new ForgeConfigSpec.Builder().configure(Server::new);
        server = serverSpecPair.getLeft();
        serverSpec = serverSpecPair.getRight();
    }
}
