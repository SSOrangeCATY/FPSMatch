package com.phasetranscrystal.fpsmatch;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class FPSMConfig {
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


    public static class Common {
        public final ForgeConfigSpec.IntValue mainWeaponCount;
        public final ForgeConfigSpec.IntValue secondaryWeaponCount;
        public final ForgeConfigSpec.IntValue thirdWeaponCount;
        public final ForgeConfigSpec.IntValue throwableCount;
        private Common(ForgeConfigSpec.Builder builder) {
            builder.push("server");
            {
                mainWeaponCount = builder.comment("Match MainWeapon Count").defineInRange("MainWeaponCount", 1,0,10);
                secondaryWeaponCount = builder.comment("Match Secondary Count").defineInRange("SecondaryCount", 1,0,10);
                throwableCount = builder.comment("Match Throwable Count").defineInRange("ThrowableCount", 4,0,10);
                thirdWeaponCount = builder.comment("Match ThirdWeapon Count").defineInRange("ThirdWeaponCount", 1,0,10);
            }
            builder.pop();
        }
    }
    public static final Client client;
    public static final ForgeConfigSpec clientSpec;
    public static final Common common;
    public static final ForgeConfigSpec commonSpec;

    static {
        final Pair<Client, ForgeConfigSpec> clientSpecPair = new ForgeConfigSpec.Builder().configure(Client::new);
        client = clientSpecPair.getLeft();
        clientSpec = clientSpecPair.getRight();
        final Pair<Common,ForgeConfigSpec> serverSpecPair = new ForgeConfigSpec.Builder().configure(Common::new);
        common = serverSpecPair.getLeft();
        commonSpec = serverSpecPair.getRight();
    }
}
