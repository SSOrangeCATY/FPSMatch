package com.phasetranscrystal.fpsmatch.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

public class FPSMConfig {
    public static class Server {
        public static ForgeConfigSpec.BooleanValue lock3PersonCamera;
        public static ForgeConfigSpec.BooleanValue lockSpecKeyHandle;
        public static ForgeConfigSpec.BooleanValue disableDefaultGlow;
        public static ForgeConfigSpec.BooleanValue disableSpecGlowKey;
        public static ForgeConfigSpec.BooleanValue disableRenderNameTag;
        public static ForgeConfigSpec.BooleanValue disableRenderHitBox;
        public static ForgeConfigSpec.BooleanValue disableRenderHeadShotHitBox;

        public static void init(ForgeConfigSpec.Builder builder) {
            lock3PersonCamera = builder.comment(
                    "禁用第三人称"
            ).define("Lock3PersonCamera", true);

            lockSpecKeyHandle = builder.comment(
                    "阻止旁观者原版按键"
            ).define("LockSpecKeyHandle", false);

            disableDefaultGlow = builder.comment(
                    "禁用原版的发光效果"
            ).define("DisableDefaultGlow", false);

            disableSpecGlowKey = builder.comment(
                    "禁用旁观者模式的发光按键"
            ).define("DisableSpecGlowKey", false);

            disableRenderNameTag = builder.comment(
                    "禁止玩家头顶名称的渲染"
            ).define("DisableRenderNameTag", false);

            builder.comment("如果取消了碰撞箱的渲染则爆头碰撞箱也不会渲染了");
            builder.comment("Disabling hit box rendering will also hide the headshot hit box");

            disableRenderHitBox = builder.comment(
                    "禁止渲染碰撞箱"
            ).define("disableRenderHitBox", false);

            disableRenderHeadShotHitBox = builder.comment(
                    "禁止渲染爆头碰撞箱"
            ).define("disableRenderHeadShotHitBox", false);
        }

    }

    public static class Client{

        private Client(ForgeConfigSpec.Builder builder) {
        }
    }


    public static class Common {

        // normal
        public final ForgeConfigSpec.BooleanValue autoAdventureMode;

        // drops
        public final ForgeConfigSpec.IntValue mainWeaponCount;
        public final ForgeConfigSpec.IntValue secondaryWeaponCount;
        public final ForgeConfigSpec.IntValue thirdWeaponCount;
        public final ForgeConfigSpec.IntValue throwableCount;

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

            builder.push("normal");
            autoAdventureMode = builder.comment(
                    "进入世界自动切换到冒险模式"
            ).define("AutoAdventureMode", true);
            builder.pop();

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

    public static Client client;
    public static ForgeConfigSpec clientSpec;
    public static Common common;
    public static ForgeConfigSpec commonSpec;
    public static ForgeConfigSpec serverSpec;

    static {
        final Pair<Client, ForgeConfigSpec> clientSpecPair = new ForgeConfigSpec.Builder().configure(Client::new);
        client = clientSpecPair.getLeft();
        clientSpec = clientSpecPair.getRight();
        final Pair<Common,ForgeConfigSpec> commonSpecPair = new ForgeConfigSpec.Builder().configure(Common::new);
        common = commonSpecPair.getLeft();
        commonSpec = commonSpecPair.getRight();
    }

    public static ForgeConfigSpec initServer(){
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        Server.init(builder);
        serverSpec = builder.build();
        return serverSpec;
    }
}
