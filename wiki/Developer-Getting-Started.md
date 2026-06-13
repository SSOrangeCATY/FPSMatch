# 从零开始：开发者教程

本文只说明“如何在自己的 Forge 模组中依赖 FPSMatch，并注册一个最小地图类型”。如果你只是使用 FPSMatch 做玩法扩展，只需要关注依赖配置、地图注册和运行时扩展点。

## 1. 添加 FPSMatch 依赖

FPSMatch 可以从 Modrinth Maven 或 CurseForge Maven 引入。二选一即可。

### 使用 Modrinth Maven

```gradle
repositories {
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
    }
}

dependencies {
    modImplementation "maven.modrinth:fpsmatch:1.2.5"
}
```

### 使用 CurseForge Maven

```gradle
repositories {
    maven {
        name = "CurseMaven"
        url = "https://www.cursemaven.com"
    }
}

dependencies {
    modImplementation "curse.maven:fpsmatch-1331710:7109977"
}
```

### 使用本地或私有 Maven

如果你把 FPSMatch 发布到了自己的 Maven，可使用它的 Gradle 坐标：

```gradle
dependencies {
    modImplementation "com.phasetranscrystal:fpsmatch:<version>"
}
```

`<version>` 替换为你实际发布的 FPSMatch 版本。

## 2. 创建最小地图类

FPSMatch 的玩法核心是 `BaseMap`。每个可运行对局都应该是一个 `BaseMap` 子类。

最小地图类需要做三件事：

1. 调用 `super(serverLevel, mapName, areaData)` 初始化地图基础数据。
2. 添加至少一个普通队伍。
3. 实现地图类型 ID 和胜利条件。

```java
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.TeamData;
import net.minecraft.server.level.ServerLevel;

public class ExampleArenaMap extends BaseMap {
    public static final String TYPE = "example_arena";

    public ExampleArenaMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData);

        // 创建两个普通队伍。第二个参数是队伍人数上限。
        this.addTeam(TeamData.of("red", 5));
        this.addTeam(TeamData.of("blue", 5));
    }

    @Override
    public String getGameType() {
        // 这个字符串就是 /fpsm map create 使用的 gameType。
        return TYPE;
    }

    @Override
    public boolean victoryGoal() {
        // 返回 true 时，BaseMap.mapTick() 会触发 victory()。
        // 最小示例先不设置胜利条件。
        return false;
    }
}
```

## 3. 注册地图类型

地图类型需要通过 `RegisterFPSMapEvent` 注册。注册后，FPSMatch 才知道如何根据 `gameType` 创建你的地图实例。

```java
import com.phasetranscrystal.fpsmatch.common.event.register.RegisterFPSMapEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ExampleMapRegister {
    @SubscribeEvent
    public static void onMapRegister(RegisterFPSMapEvent event) {
        // TYPE 对应地图类型 ID，ExampleArenaMap::new 对应地图构造器。
        event.registerGameType(ExampleArenaMap.TYPE, ExampleArenaMap::new);
    }
}
```

这里的 `MODID` 应替换为你的模组 ID 常量。

## 4. 创建地图实例

进入游戏后，用 `/fpsm map create` 创建地图实例：

```text
/fpsm map create example_arena test_map ~ ~ ~ ~10 ~10 ~10
```

参数说明：

- `example_arena`：地图类型 ID，对应 `ExampleArenaMap.TYPE`。
- `test_map`：地图实例名称，同一地图类型下不能重复。
- 第一个坐标：地图区域的第一个角点。
- 第二个坐标：地图区域的第二个角点。

地图区域由 `AreaData` 保存。FPSMatch 会用这个区域判断玩家、实体或方块是否位于地图内。

## 5. 加入地图并启动

让当前玩家加入地图：

```text
/fpsm map modify example_arena test_map team join @s
```

启动地图：

```text
/fpsm map modify example_arena test_map debug start
```

`debug start` 会调用 `BaseMap.start()`。如果没有事件取消，地图会进入开始状态。

## 6. 添加胜利条件

`victoryGoal()` 每 tick 都会被 `BaseMap.mapTick()` 调用。你可以在这里检查队伍分数、存活人数、时间限制或目标状态。

```java
@Override
public boolean victoryGoal() {
    ServerTeam red = this.getMapTeams().getTeamByName("red");
    ServerTeam blue = this.getMapTeams().getTeamByName("blue");

    // 示例：任意队伍达到 10 分后结束地图。
    return red.getScores() >= 10 || blue.getScores() >= 10;
}
```

如果 `victoryGoal()` 返回 `true`，FPSMatch 会调用 `victory()` 并发布 `FPSMapEvent.VictoryEvent`。

## 7. 处理玩家死亡

FPSMatch 会把对局内死亡信息包装成 `DeathContext`。自定义玩法通常需要覆盖 `handleDeath` 来处理分数、回合状态或复活逻辑。

```java
@Override
public void handleDeath(DeathContext context) {
    super.handleDeath(context);

    // 死亡玩家一定存在。
    ServerPlayer deadPlayer = context.getDeadPlayer();

    // 攻击者可能不存在，例如摔落、虚空或环境伤害。
    context.getAttackerOptional().ifPresent(attacker -> {
        ServerTeam attackerTeam = this.getMapTeams().getTeamByPlayer(attacker);
        attackerTeam.setScores(attackerTeam.getScores() + 1);
    });
}
```

`DeathContext` 还会携带枪杀、爆头、穿墙、穿烟、开镜击杀等信息。是否有这些信息取决于实际伤害来源和兼容事件。

## 8. 添加地图配置项

如果你的地图需要可调参数，可以使用 `Setting<T>`。配置项可以通过命令读取和修改。

```java
private final Setting<Integer> scoreLimit = new Setting<>(
        "scoreLimit",
        Codec.INT,
        Integer::parseInt,
        10
);

public ExampleArenaMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
    super(serverLevel, mapName, areaData);
    this.addTeam(TeamData.of("red", 5));
    this.addTeam(TeamData.of("blue", 5));

    // 注册后即可通过 /fpsm map modify ... settings 操作。
    this.addSetting(scoreLimit);
}

@Override
public boolean victoryGoal() {
    return this.getMapTeams().getNormalTeams().stream()
            .anyMatch(team -> team.getScores() >= scoreLimit.get());
}
```

修改配置：

```text
/fpsm map modify example_arena test_map settings set scoreLimit 15
```

## 9. 下一步

最小地图跑通后，再继续接入：

- `MapCapability`：地图级可复用功能。
- `TeamCapability`：队伍级可复用功能。
- `FPSMShop`：经济和购买系统。
- `FPSMapEvent`：对局事件监听。
- 自定义 packet：同步额外客户端数据。
