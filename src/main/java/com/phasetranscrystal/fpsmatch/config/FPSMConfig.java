package com.phasetranscrystal.fpsmatch.config;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class FPSMConfig {
    public static class Server {
        public static ModConfigSpec.BooleanValue lock3PersonCamera;
        public static ModConfigSpec.BooleanValue lockSpecKeyHandle;
        public static ModConfigSpec.BooleanValue disableDefaultGlow;
        public static ModConfigSpec.BooleanValue disableSpecGlowKey;
        public static ModConfigSpec.BooleanValue disableRenderNameTag;
        public static ModConfigSpec.BooleanValue disableRenderHitBox;
        public static ModConfigSpec.BooleanValue disableRenderHeadShotHitBox;
        public static ModConfigSpec.BooleanValue enableMapSelectionButtonForNonOps;

        public static ModConfigSpec.IntValue minimapEditorPermissionLevel;
        public static ModConfigSpec.IntValue minimapEditorSessionTtlMinutes;
        public static ModConfigSpec.IntValue minimapDraftTtlDays;
        public static ModConfigSpec.IntValue minimapUploadTtlMinutes;
        public static ModConfigSpec.IntValue minimapPublishTokenTtlMinutes;
        public static ModConfigSpec.IntValue minimapMarkerHz;
        public static ModConfigSpec.BooleanValue minimapDirtyTrackingEnabled;
        public static ModConfigSpec.BooleanValue minimapObserverOmniscient;
        public static ModConfigSpec.IntValue minimapIntelligenceTtlTicks;
        public static ModConfigSpec.IntValue minimapSectionStateSaveIntervalTicks;

        public static ModConfigSpec.IntValue minimapMaxCanvasEdge;
        public static ModConfigSpec.IntValue minimapMaxFloors;
        public static ModConfigSpec.IntValue minimapMaxSourceLayers;
        public static ModConfigSpec.IntValue minimapMaxRegions;
        public static ModConfigSpec.IntValue minimapMaxVectorVertices;
        public static ModConfigSpec.IntValue minimapMaxZipEntries;
        public static ModConfigSpec.IntValue minimapMaxManifestMiB;
        public static ModConfigSpec.IntValue minimapMaxSourceExpandedMiB;
        public static ModConfigSpec.IntValue minimapMaxRuntimeExpandedMiB;
        public static ModConfigSpec.IntValue minimapMaxCanonicalPngMiB;
        public static ModConfigSpec.IntValue minimapMaxTileEdge;
        public static ModConfigSpec.IntValue minimapSnapshotBudgetMillis;
        public static ModConfigSpec.IntValue minimapSnapshotBudgetKiB;

        public static void init(ModConfigSpec.Builder builder) {
            lock3PersonCamera = builder.comment(
                    "禁用第三人称"
            ).define("Lock3PersonCamera", true);

            lockSpecKeyHandle = builder.comment(
                    "阻止旁观者原版按键"
            ).define("LockSpecKeyHandle", true);

            disableDefaultGlow = builder.comment(
                    "禁用原版的发光效果"
            ).define("DisableDefaultGlow", true);

            disableSpecGlowKey = builder.comment(
                    "禁用旁观者模式的发光按键"
            ).define("DisableSpecGlowKey", true);

            disableRenderNameTag = builder.comment(
                    "禁止玩家头顶名称的渲染"
            ).define("DisableRenderNameTag", true);

            builder.comment("如果取消了碰撞箱的渲染则爆头碰撞箱也不会渲染了");
            builder.comment("Disabling hit box rendering will also hide the headshot hit box");

            disableRenderHitBox = builder.comment(
                    "禁止渲染碰撞箱"
            ).define("disableRenderHitBox", true);

            disableRenderHeadShotHitBox = builder.comment(
                    "禁止渲染爆头碰撞箱"
            ).define("disableRenderHeadShotHitBox", true);

            enableMapSelectionButtonForNonOps = builder.comment(
                    "允许非 OP 玩家在 ESC 暂停界面看到 FPSMatch 地图选择按钮",
                    "Allow non-OP players to see the FPSMatch map selection button in the ESC pause screen"
            ).define("EnableMapSelectionButtonForNonOps", true);

            builder.comment("Server-side minimap editor and runtime settings")
                    .push("minimap");

            minimapEditorPermissionLevel = builder.comment(
                    "Required operator permission level for every minimap editor action",
                    "The default level 2 may only be raised"
            ).defineInRange("editorPermissionLevel", 2, 2, 4);
            minimapEditorSessionTtlMinutes = builder.comment(
                    "Idle editor-session lifetime in minutes"
            ).defineInRange("editorSessionTtlMinutes", 10, 1, 1440);
            minimapDraftTtlDays = builder.comment(
                    "Inactive draft lifetime in days"
            ).defineInRange("draftTtlDays", 7, 1, 365);
            minimapUploadTtlMinutes = builder.comment(
                    "Inactive temporary-upload lifetime in minutes"
            ).defineInRange("uploadTtlMinutes", 30, 1, 1440);
            minimapPublishTokenTtlMinutes = builder.comment(
                    "Lifetime of a reserved one-use publish token in minutes"
            ).defineInRange("publishTokenTtlMinutes", 30, 1, 1440);
            minimapMarkerHz = builder.comment(
                    "Dynamic minimap marker update frequency"
            ).defineInRange("markerHz", 5, 1, 20);
            minimapDirtyTrackingEnabled = builder.comment(
                    "Global master gate for incremental world-section dirty tracking"
            ).define("dirtyTrackingEnabled", false);
            minimapObserverOmniscient = builder.comment(
                    "Allow true observers to receive all policy-approved minimap markers"
            ).define("observerOmniscient", true);
            minimapIntelligenceTtlTicks = builder.comment(
                    "Default lifetime of last-known enemy intelligence in game ticks"
            ).defineInRange("intelligenceTtlTicks", 60, 0, 1200);
            minimapSectionStateSaveIntervalTicks = builder.comment(
                    "Normal persistence interval for world-section revision state"
            ).defineInRange("sectionStateSaveIntervalTicks", 1200, 20, 12000);
            minimapMaxCanvasEdge = builder.comment(
                    "Business quota for one canvas edge"
            ).defineInRange("maxCanvasEdge", 8192, 1, MinimapHardLimits.MAX_CANVAS_EDGE);
            minimapMaxFloors = builder.comment(
                    "Business quota for floors per minimap"
            ).defineInRange("maxFloors", 16, 1, MinimapHardLimits.MAX_FLOORS);
            minimapMaxSourceLayers = builder.comment(
                    "Business quota for total source layers"
            ).defineInRange("maxSourceLayers", 128, 1, MinimapHardLimits.MAX_SOURCE_LAYERS);
            minimapMaxRegions = builder.comment(
                    "Business quota for regions"
            ).defineInRange("maxRegions", 4096, 0, MinimapHardLimits.MAX_REGIONS);
            minimapMaxVectorVertices = builder.comment(
                    "Business quota for aggregate vector vertices"
            ).defineInRange("maxVectorVertices", 65536, 0, MinimapHardLimits.MAX_VECTOR_VERTICES);
            minimapMaxZipEntries = builder.comment(
                    "Business quota for canonical container entries"
            ).defineInRange("maxZipEntries", 16384, 1, MinimapHardLimits.MAX_ZIP_ENTRIES);
            minimapMaxManifestMiB = builder.comment(
                    "Business quota for either source or runtime manifest, in MiB"
            ).defineInRange("maxManifestMiB", 2, 1, Math.min(
                    hardLimitMiB(MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES),
                    hardLimitMiB(MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES)
            ));
            minimapMaxSourceExpandedMiB = builder.comment(
                    "Business quota for expanded .fpsmap bytes, in MiB"
            ).defineInRange("maxSourceExpandedMiB", 512, 1,
                    hardLimitMiB(MinimapHardLimits.MAX_SOURCE_EXPANDED_BYTES));
            minimapMaxRuntimeExpandedMiB = builder.comment(
                    "Business quota for expanded .fpsmapc bytes, in MiB"
            ).defineInRange("maxRuntimeExpandedMiB", 256, 1,
                    hardLimitMiB(MinimapHardLimits.MAX_RUNTIME_EXPANDED_BYTES));
            minimapMaxCanonicalPngMiB = builder.comment(
                    "Business quota for one canonical compressed PNG, in MiB"
            ).defineInRange("maxCanonicalPngMiB", 64, 1,
                    hardLimitMiB(MinimapHardLimits.MAX_CANONICAL_PNG_COMPRESSED_BYTES));
            minimapMaxTileEdge = builder.comment(
                    "Business quota for the tile edge in pixels"
            ).defineInRange("maxTileEdge", 512, 1, MinimapHardLimits.MAX_TILE_EDGE);
            minimapSnapshotBudgetMillis = builder.comment(
                    "Maximum server-thread snapshot copy budget per tick, in milliseconds"
            ).defineInRange("snapshotBudgetMillis", 2, 1, 5);
            minimapSnapshotBudgetKiB = builder.comment(
                    "Maximum snapshot bytes scheduled per tick, in KiB"
            ).defineInRange("snapshotBudgetKiB", 512, 1, 512);

            builder.pop();
        }

        private static int hardLimitMiB(long bytes) {
            return Math.toIntExact(bytes / (1024L * 1024L));
        }

    }

    public static class Client{

        private static final Set<String> HUD_ANCHORS = Set.of(
                "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"
        );
        private static final Set<String> CLIP_SHAPES = Set.of("SQUARE", "CIRCLE");
        private static final Set<String> DEFAULT_MODES = Set.of(
                "DOCUMENT", "FIXED_NORTH", "FOLLOW_PLAYER"
        );
        private static final Set<String> ADJACENT_FLOOR_STYLES = Set.of(
                "HIDDEN", "FADED_ARROWS"
        );
        private static final Pattern MARKER_NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");
        private static final Pattern MARKER_PATH = Pattern.compile("[a-z0-9/._-]{1,256}");

        public final ModConfigSpec.BooleanValue minimapEnabled;
        public final ModConfigSpec.IntValue minimapPreferredSize;
        public final ModConfigSpec.IntValue minimapMinimumSize;
        public final ModConfigSpec.ConfigValue<String> minimapHudAnchor;
        public final ModConfigSpec.IntValue minimapHudMarginX;
        public final ModConfigSpec.IntValue minimapHudMarginY;
        public final ModConfigSpec.IntValue minimapHudSafeAreaPriority;
        public final ModConfigSpec.ConfigValue<String> minimapClipShape;
        public final ModConfigSpec.DoubleValue minimapOpacity;
        public final ModConfigSpec.DoubleValue minimapBackgroundOpacity;
        public final ModConfigSpec.ConfigValue<String> minimapDefaultMode;
        public final ModConfigSpec.DoubleValue minimapFollowZoom;
        public final ModConfigSpec.BooleanValue minimapShowRegionLabels;
        public final ModConfigSpec.BooleanValue minimapShowFloorLabel;
        public final ModConfigSpec.BooleanValue minimapShowCompass;
        public final ModConfigSpec.ConfigValue<String> minimapAdjacentFloorMarkerStyle;
        public final ModConfigSpec.ConfigValue<String> minimapMarkerFilterCsv;
        public final ModConfigSpec.IntValue minimapManualFloorTimeoutTicks;
        public final ModConfigSpec.IntValue minimapCacheMiB;

        private Client(ModConfigSpec.Builder builder) {
            builder.comment("Client-side minimap display settings")
                    .push("minimap");
            minimapEnabled = builder.define("enabled", true);
            minimapPreferredSize = builder.defineInRange("preferredSize", 128, 96, 512);
            minimapMinimumSize = builder.defineInRange("minimumSize", 96, 64, 512);
            minimapHudAnchor = builder.define("hudAnchor", "TOP_LEFT", value ->
                    value instanceof String anchor && HUD_ANCHORS.contains(anchor));
            minimapHudMarginX = builder.defineInRange("hudMarginX", 12, 0, 256);
            minimapHudMarginY = builder.defineInRange("hudMarginY", 12, 0, 256);
            minimapHudSafeAreaPriority = builder.defineInRange("hudSafeAreaPriority", 50, 0, 1000);
            minimapClipShape = builder.define("clipShape", "SQUARE", value ->
                    value instanceof String shape && CLIP_SHAPES.contains(shape));
            minimapOpacity = builder.defineInRange("opacity", 1.0, 0.0, 1.0);
            minimapBackgroundOpacity = builder.defineInRange("backgroundOpacity", 0.6, 0.0, 1.0);
            minimapDefaultMode = builder.define("defaultMode", "DOCUMENT", value ->
                    value instanceof String mode && DEFAULT_MODES.contains(mode));
            minimapFollowZoom = builder.defineInRange("followZoom", 1.0, 0.25, 8.0);
            minimapShowRegionLabels = builder.define("showRegionLabels", true);
            minimapShowFloorLabel = builder.define("showFloorLabel", true);
            minimapShowCompass = builder.define("showCompass", true);
            minimapAdjacentFloorMarkerStyle = builder.define(
                    "adjacentFloorMarkerStyle", "FADED_ARROWS",
                    value -> value instanceof String style && ADJACENT_FLOOR_STYLES.contains(style));
            minimapMarkerFilterCsv = builder.define(
                    "markerFilterCsv", "", Client::validMarkerFilterCsv);
            minimapManualFloorTimeoutTicks = builder.defineInRange(
                    "manualFloorTimeoutTicks", 100, 20, 1200);
            minimapCacheMiB = builder.defineInRange("cacheMiB", 256, 64, 4096);
            builder.pop();
        }

        private static boolean validMarkerFilterCsv(Object candidate) {
            if (!(candidate instanceof String value) || value.isEmpty()) {
                return candidate instanceof String;
            }
            for (String id : value.split(",", -1)) {
                int separator = id.indexOf(':');
                if (separator <= 0 || separator != id.lastIndexOf(':')
                        || separator == id.length() - 1
                        || !MARKER_NAMESPACE.matcher(id.substring(0, separator)).matches()) {
                    return false;
                }
                String path = id.substring(separator + 1);
                if (!MARKER_PATH.matcher(path).matches()) {
                    return false;
                }
                for (String segment : path.split("/", -1)) {
                    if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                        return false;
                    }
                }
            }
            return true;
        }
    }


    public static class Common {

        // normal
        public final ModConfigSpec.BooleanValue autoAdventureMode;

        public final ModConfigSpec.DoubleValue baseArmorPenetration;
        public final ModConfigSpec.DoubleValue headshotMultiplier;

        // drops
        public final ModConfigSpec.IntValue mainWeaponCount;
        public final ModConfigSpec.IntValue secondaryWeaponCount;
        public final ModConfigSpec.IntValue thirdWeaponCount;
        public final ModConfigSpec.IntValue throwableCount;

        // Flash Bomb
        public final ModConfigSpec.IntValue flashBombRadius;
        // Grenade
        public final ModConfigSpec.IntValue grenadeRadius;
        public final ModConfigSpec.IntValue grenadeFuseTime;
        public final ModConfigSpec.IntValue grenadeDamage;
        // Incendiary Grenade
        public final ModConfigSpec.IntValue incendiaryGrenadeOutTime;
        public final ModConfigSpec.IntValue incendiaryGrenadeLivingTime;
        public final ModConfigSpec.IntValue incendiaryGrenadeDamage;
        // SmokeShell
        public final ModConfigSpec.IntValue smokeShellLivingTime;

        private Common(ModConfigSpec.Builder builder) {

            builder.push("normal");
            autoAdventureMode = builder.comment(
                    "进入世界自动切换到冒险模式"
            ).define("AutoAdventureMode", true);
            builder.pop();

            builder.push("armor");
            {
                baseArmorPenetration = builder.comment(
                        "防弹衣的基础穿透系数",
                        "Base armor penetration multiplier",
                        "当玩家有防弹衣时，受到的伤害 = 原伤害 * (baseArmorPenetration / 2.0)"
                ).defineInRange("BaseArmorPenetration", 1.4, 0.1, 5.0);

                headshotMultiplier = builder.comment(
                        "爆头伤害倍率",
                        "Headshot damage multiplier"
                ).defineInRange("HeadshotMultiplier", 4.0, 1.0, 10.0);
            }
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
    public static ModConfigSpec clientSpec;
    public static Common common;
    public static ModConfigSpec commonSpec;
    public static ModConfigSpec serverSpec;

    static {
        final Pair<Client, ModConfigSpec> clientSpecPair = new ModConfigSpec.Builder().configure(Client::new);
        client = clientSpecPair.getLeft();
        clientSpec = clientSpecPair.getRight();
        final Pair<Common,ModConfigSpec> commonSpecPair = new ModConfigSpec.Builder().configure(Common::new);
        common = commonSpecPair.getLeft();
        commonSpec = commonSpecPair.getRight();
    }

    public static synchronized ModConfigSpec initServer(){
        if (serverSpec != null) {
            return serverSpec;
        }
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        Server.init(builder);
        serverSpec = builder.build();
        return serverSpec;
    }
}
