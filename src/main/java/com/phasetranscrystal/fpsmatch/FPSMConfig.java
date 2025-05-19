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
        // drops
        public final ForgeConfigSpec.IntValue mainWeaponCount;
        public final ForgeConfigSpec.IntValue secondaryWeaponCount;
        public final ForgeConfigSpec.IntValue thirdWeaponCount;
        public final ForgeConfigSpec.IntValue throwableCount;

        // -throwable-
        // Flash Bomb
        public final ForgeConfigSpec.IntValue flashBombRadius;
        // Grenade
        public final ForgeConfigSpec.IntValue grenadeRadius;
        public final ForgeConfigSpec.IntValue grenadeFuseTime;
        public final ForgeConfigSpec.IntValue grenadeDamage;
        // Incendiary Grenade
        public final ForgeConfigSpec.IntValue incendiaryGrenadeOutTime;
        public final ForgeConfigSpec.IntValue incendiaryGrenadeLivingTime;
        public final ForgeConfigSpec.IntValue incendiaryGrenadeDamage;
        // SmokeShell
        public final ForgeConfigSpec.IntValue smokeShellLivingTime;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.push("drops");
            {
                mainWeaponCount = builder.comment(
                        "比赛时主武器可拾取数量",
                        "Number of main weapons that can be picked up during the competition"
                ).defineInRange("MainWeaponCount", 1,0,10);
                secondaryWeaponCount = builder.comment(
                        "比赛时副武器可拾取数量",
                        "Number of secondary weapons that can be picked up during the competition"
                ).defineInRange("SecondaryCount", 1,0,10);
                throwableCount = builder.comment(
                        "比赛时投掷物可拾取数量",
                        "Number of throwable that can be picked up during the competition")
                        .defineInRange("ThrowableCount", 4,0,10);
                thirdWeaponCount = builder.comment(
                        "比赛时RPG品类(刀包用)可拾取数量",
                        "The number of weapons that can be picked up when the weapon type is RPG (knife) during the competition"
                ).defineInRange("ThirdWeaponCount", 1,0,10);
            }
            builder.pop();

            builder.push("throwable");
            {
                flashBombRadius = builder.comment(
                        "闪光弹致盲生效半径",
                        "Effective blinding radius of flash bombs"
                ).defineInRange("FlashBombRadius", 48, 0, 48);

                grenadeRadius = builder.comment(
                        "手雷爆炸生效半径",
                        "Effective explosion radius of grenades"
                ).defineInRange("GrenadeRadius", 3, 0, 10);

                grenadeFuseTime = builder.comment(
                        "手雷投掷后多久爆炸 (tick)",
                        "Delay before grenade detonation after being thrown (ticks)",
                        "20 ticks = 1 second"
                ).defineInRange("GrenadeFuseTime", 30, 0, 200);

                grenadeDamage = builder.comment(
                        "手雷的爆炸伤害",
                        "Explosion damage of grenades"
                ).defineInRange("GrenadeDamage", 20, 0, 9999);

                incendiaryGrenadeOutTime = builder.comment(
                        "燃烧弹投掷后多久自毁 (tick)",
                        "Self-destruct delay of incendiary grenades after being thrown (ticks)",
                        "20 ticks = 1 second"
                ).defineInRange("IncendiaryGrenadeOutTime", 40, 0, 200);

                incendiaryGrenadeLivingTime = builder.comment(
                        "燃烧弹激活后的存活时间 (tick)",
                        "Survival time after activation of incendiary grenade (ticks)",
                        "20 ticks = 1 second"
                ).defineInRange("IncendiaryGrenadeLivingTime", 140, 0, 400);

                incendiaryGrenadeDamage = builder.comment(
                        "燃烧弹的伤害",
                        "Damage value of incendiary grenades"
                ).defineInRange("IncendiaryGrenadeDamage", 2, 0, 9999);

                smokeShellLivingTime = builder.comment(
                        "烟雾弹激活后的存活时间 (tick)",
                        "Survival time after smoke bomb activation (ticks)",
                        "20 ticks = 1 second"
                ).defineInRange("SmokeShellLivingTime", 300, 0, 900);
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
