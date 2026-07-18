package com.phasetranscrystal.fpsmatch.config;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class FPSMConfig {
    public static class Server {
        public static ForgeConfigSpec.BooleanValue lock3PersonCamera;
        public static ForgeConfigSpec.BooleanValue lockSpecKeyHandle;
        public static ForgeConfigSpec.BooleanValue disableDefaultGlow;
        public static ForgeConfigSpec.BooleanValue disableSpecGlowKey;
        public static ForgeConfigSpec.BooleanValue disableRenderNameTag;
        public static ForgeConfigSpec.BooleanValue disableRenderHitBox;
        public static ForgeConfigSpec.BooleanValue disableRenderHeadShotHitBox;
        public static ForgeConfigSpec.BooleanValue enableMapSelectionButtonForNonOps;

        public static ForgeConfigSpec.BooleanValue roomSyncPushEnabled;
        public static ForgeConfigSpec.IntValue roomSyncIntervalTicks;
        public static ForgeConfigSpec.IntValue roomSyncMaxWatchersPerRoom;

        public static ForgeConfigSpec.IntValue minimapEditorPermissionLevel;
        public static ForgeConfigSpec.IntValue minimapEditorSessionTtlMinutes;
        public static ForgeConfigSpec.IntValue minimapDraftTtlDays;
        public static ForgeConfigSpec.IntValue minimapUploadTtlMinutes;
        public static ForgeConfigSpec.IntValue minimapPublishTokenTtlMinutes;
        public static ForgeConfigSpec.IntValue minimapMarkerHz;
        public static ForgeConfigSpec.BooleanValue minimapDirtyTrackingEnabled;
        public static ForgeConfigSpec.BooleanValue minimapObserverOmniscient;
        public static ForgeConfigSpec.IntValue minimapIntelligenceTtlTicks;
        public static ForgeConfigSpec.IntValue minimapSectionStateSaveIntervalTicks;

        public static ForgeConfigSpec.IntValue minimapMaxCanvasEdge;
        public static ForgeConfigSpec.IntValue minimapMaxFloors;
        public static ForgeConfigSpec.IntValue minimapMaxSourceLayers;
        public static ForgeConfigSpec.IntValue minimapMaxRegions;
        public static ForgeConfigSpec.IntValue minimapMaxVectorVertices;
        public static ForgeConfigSpec.IntValue minimapMaxZipEntries;
        public static ForgeConfigSpec.IntValue minimapMaxManifestMiB;
        public static ForgeConfigSpec.IntValue minimapMaxSourceExpandedMiB;
        public static ForgeConfigSpec.IntValue minimapMaxRuntimeExpandedMiB;
        public static ForgeConfigSpec.IntValue minimapMaxCanonicalPngMiB;
        public static ForgeConfigSpec.IntValue minimapMaxTileEdge;
        public static ForgeConfigSpec.IntValue minimapSnapshotBudgetMillis;
        public static ForgeConfigSpec.IntValue minimapSnapshotBudgetKiB;

        public static void init(ForgeConfigSpec.Builder builder) {
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

            builder.comment(
                    "房间/地图选择界面实时同步（订阅式事件驱动）",
                    "Room / map-selection UI live sync (subscription-based, event-driven)"
            ).push("roomSync");

            roomSyncPushEnabled = builder.comment(
                    "启用后：房间数据变化时主动向正在观看的客户端广播，实现准实时刷新；",
                    "关闭后回退到旧的“打开/手动刷新才拉取一次”行为。",
                    "When enabled, room changes are pushed to viewers for near-realtime UI; disabled falls back to legacy pull-on-open."
            ).define("RoomSyncPushEnabled", true);

            roomSyncIntervalTicks = builder.comment(
                    "服务端合并/广播变更的间隔(tick)。越小越实时越耗带宽，20=每秒一次。",
                    "Server dirty-scan / broadcast interval in ticks. Smaller = more realtime & more bandwidth."
            ).defineInRange("RoomSyncIntervalTicks", 10, 1, 100);

            roomSyncMaxWatchersPerRoom = builder.comment(
                    "单个房间单次广播的最大接收者数量，防止大厅广播风暴。",
                    "Max recipients per room per broadcast, to prevent lobby broadcast storms."
            ).defineInRange("RoomSyncMaxWatchersPerRoom", 64, 1, 512);

            builder.pop();

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
                    "Global master gate for incremental world-section dirty tracking",
                    "Per-map overrides are capability settings and cannot bypass this gate"
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
                    "Business quota for one canvas edge; cannot exceed the compiled hard limit"
            ).defineInRange("maxCanvasEdge", 8192, 1, MinimapHardLimits.MAX_CANVAS_EDGE);

            minimapMaxFloors = builder.comment(
                    "Business quota for floors per minimap"
            ).defineInRange("maxFloors", 16, 1, MinimapHardLimits.MAX_FLOORS);

            minimapMaxSourceLayers = builder.comment(
                    "Business quota for total source layers"
            ).defineInRange(
                    "maxSourceLayers", 128, 1, MinimapHardLimits.MAX_SOURCE_LAYERS
            );

            minimapMaxRegions = builder.comment(
                    "Business quota for regions"
            ).defineInRange("maxRegions", 4096, 0, MinimapHardLimits.MAX_REGIONS);

            minimapMaxVectorVertices = builder.comment(
                    "Business quota for aggregate vector vertices"
            ).defineInRange(
                    "maxVectorVertices", 65536, 0, MinimapHardLimits.MAX_VECTOR_VERTICES
            );

            minimapMaxZipEntries = builder.comment(
                    "Business quota for canonical container entries"
            ).defineInRange(
                    "maxZipEntries", 16384, 1, MinimapHardLimits.MAX_ZIP_ENTRIES
            );

            minimapMaxManifestMiB = builder.comment(
                    "Business quota for either source or runtime manifest, in MiB"
            ).defineInRange(
                    "maxManifestMiB",
                    2,
                    1,
                    Math.min(
                            hardLimitMiB(MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES),
                            hardLimitMiB(MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES)
                    )
            );

            minimapMaxSourceExpandedMiB = builder.comment(
                    "Business quota for expanded .fpsmap bytes, in MiB"
            ).defineInRange(
                    "maxSourceExpandedMiB",
                    512,
                    1,
                    hardLimitMiB(MinimapHardLimits.MAX_SOURCE_EXPANDED_BYTES)
            );

            minimapMaxRuntimeExpandedMiB = builder.comment(
                    "Business quota for expanded .fpsmapc bytes, in MiB"
            ).defineInRange(
                    "maxRuntimeExpandedMiB",
                    256,
                    1,
                    hardLimitMiB(MinimapHardLimits.MAX_RUNTIME_EXPANDED_BYTES)
            );

            minimapMaxCanonicalPngMiB = builder.comment(
                    "Business quota for one canonical compressed PNG, in MiB"
            ).defineInRange(
                    "maxCanonicalPngMiB",
                    64,
                    1,
                    hardLimitMiB(MinimapHardLimits.MAX_CANONICAL_PNG_COMPRESSED_BYTES)
            );

            minimapMaxTileEdge = builder.comment(
                    "Business quota for the tile edge in pixels"
            ).defineInRange("maxTileEdge", 512, 1, MinimapHardLimits.MAX_TILE_EDGE);

            minimapSnapshotBudgetMillis = builder.comment(
                    "Maximum server-thread snapshot copy budget per tick, in milliseconds"
            ).defineInRange("snapshotBudgetMillis", 2, 1, 5);

            minimapSnapshotBudgetKiB = builder.comment(
                    "Maximum snapshot bytes scheduled per tick, in KiB; this setting may only tighten the 512 KiB default"
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

        public final ForgeConfigSpec.BooleanValue minimapEnabled;
        public final ForgeConfigSpec.IntValue minimapPreferredSize;
        public final ForgeConfigSpec.IntValue minimapMinimumSize;
        public final ForgeConfigSpec.ConfigValue<String> minimapHudAnchor;
        public final ForgeConfigSpec.IntValue minimapHudMarginX;
        public final ForgeConfigSpec.IntValue minimapHudMarginY;
        public final ForgeConfigSpec.IntValue minimapHudSafeAreaPriority;
        public final ForgeConfigSpec.ConfigValue<String> minimapClipShape;
        public final ForgeConfigSpec.DoubleValue minimapOpacity;
        public final ForgeConfigSpec.DoubleValue minimapBackgroundOpacity;
        public final ForgeConfigSpec.ConfigValue<String> minimapDefaultMode;
        public final ForgeConfigSpec.DoubleValue minimapFollowZoom;
        public final ForgeConfigSpec.BooleanValue minimapShowRegionLabels;
        public final ForgeConfigSpec.BooleanValue minimapShowFloorLabel;
        public final ForgeConfigSpec.BooleanValue minimapShowCompass;
        public final ForgeConfigSpec.ConfigValue<String> minimapAdjacentFloorMarkerStyle;
        public final ForgeConfigSpec.ConfigValue<String> minimapMarkerFilterCsv;
        public final ForgeConfigSpec.IntValue minimapManualFloorTimeoutTicks;
        public final ForgeConfigSpec.IntValue minimapCacheMiB;

        private Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Client-side minimap display settings")
                    .push("minimap");

            minimapEnabled = builder.comment(
                    "Enable the match minimap when the current map mounts its capability"
            ).define("enabled", true);

            minimapPreferredSize = builder.comment(
                    "Preferred HUD minimap size in screen pixels"
            ).defineInRange("preferredSize", 128, 96, 512);

            minimapMinimumSize = builder.comment(
                    "Minimum HUD minimap size used by safe-area placement"
            ).defineInRange("minimumSize", 96, 64, 512);

            minimapHudAnchor = builder.comment(
                    "Preferred HUD anchor before deterministic safe-area fallbacks"
            ).define("hudAnchor", "TOP_LEFT", value ->
                    value instanceof String anchor && HUD_ANCHORS.contains(anchor)
            );

            minimapHudMarginX = builder.comment(
                    "Horizontal HUD edge margin in screen pixels"
            ).defineInRange("hudMarginX", 12, 0, 256);

            minimapHudMarginY = builder.comment(
                    "Vertical HUD edge margin in screen pixels"
            ).defineInRange("hudMarginY", 12, 0, 256);

            minimapHudSafeAreaPriority = builder.comment(
                    "Safe-area placement priority for the minimap"
            ).defineInRange("hudSafeAreaPriority", 50, 0, 1000);

            minimapClipShape = builder.comment(
                    "HUD minimap clip shape"
            ).define("clipShape", "SQUARE", value ->
                    value instanceof String shape && CLIP_SHAPES.contains(shape)
            );

            minimapOpacity = builder.comment(
                    "Overall HUD minimap opacity"
            ).defineInRange("opacity", 1.0, 0.0, 1.0);

            minimapBackgroundOpacity = builder.comment(
                    "HUD minimap background opacity"
            ).defineInRange("backgroundOpacity", 0.6, 0.0, 1.0);

            minimapDefaultMode = builder.comment(
                    "DOCUMENT inherits the current map default before the FIXED_NORTH fallback"
            ).define("defaultMode", "DOCUMENT", value ->
                    value instanceof String mode && DEFAULT_MODES.contains(mode)
            );

            minimapFollowZoom = builder.comment(
                    "Player-follow HUD zoom multiplier"
            ).defineInRange("followZoom", 1.0, 0.25, 8.0);

            minimapShowRegionLabels = builder.comment(
                    "Show region labels on the minimap"
            ).define("showRegionLabels", true);

            minimapShowFloorLabel = builder.comment(
                    "Show the current floor label"
            ).define("showFloorLabel", true);

            minimapShowCompass = builder.comment(
                    "Show minimap compass direction"
            ).define("showCompass", true);

            minimapAdjacentFloorMarkerStyle = builder.comment(
                    "Presentation for policy-approved markers on adjacent floors"
            ).define(
                    "adjacentFloorMarkerStyle",
                    "FADED_ARROWS",
                    value -> value instanceof String style
                            && ADJACENT_FLOOR_STYLES.contains(style)
            );

            minimapMarkerFilterCsv = builder.comment(
                    "Comma-separated marker type IDs hidden by the local client; empty means no filter"
            ).define("markerFilterCsv", "", Client::validMarkerFilterCsv);

            minimapManualFloorTimeoutTicks = builder.comment(
                    "Tactical-map manual floor timeout in game ticks"
            ).defineInRange("manualFloorTimeoutTicks", 100, 20, 1200);

            minimapCacheMiB = builder.comment(
                    "Client minimap disk-cache capacity in MiB"
            ).defineInRange("cacheMiB", 256, 64, 4096);

            builder.pop();
        }

        private static boolean validMarkerFilterCsv(Object candidate) {
            if (!(candidate instanceof String value)) {
                return false;
            }
            if (value.isEmpty()) {
                return true;
            }
            for (String id : value.split(",", -1)) {
                int separator = id.indexOf(':');
                if (separator <= 0
                        || separator != id.lastIndexOf(':')
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
        public final ForgeConfigSpec.BooleanValue autoAdventureMode;

        public final ForgeConfigSpec.DoubleValue baseArmorPenetration;
        public final ForgeConfigSpec.DoubleValue headshotMultiplier;

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

    public static synchronized ForgeConfigSpec initServer(){
        if (serverSpec != null) {
            return serverSpec;
        }
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        Server.init(builder);
        serverSpec = builder.build();
        return serverSpec;
    }
}
