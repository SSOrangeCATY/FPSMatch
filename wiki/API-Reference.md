# API 与接口文档

本文按“开发时会怎么用”组织 FPSMatch API。每个条目都说明用途、调用时机和注意事项，而不是只列方法名。

## `FPSMatch`

`FPSMatch` 是 Mod 主类。外部开发者最常用的是它的网络发送辅助方法。

### `MODID`

值为 `fpsmatch`。注册资源、定位通道或判断依赖时会用到。

### `sendToPlayer(ServerPlayer player, M message)`

从服务端向指定玩家发送 S2C packet。

适合用于：

- 同步 HUD 数据。
- 同步地图选择界面。
- 同步商店、队伍、旁观状态。

```java
FPSMatch.sendToPlayer(serverPlayer, new MySyncPacket(mapName, score));
```

### `sendToServer(M message)`

从客户端向服务端发送 C2S packet。

适合用于：

- 玩家点击客户端按钮。
- 玩家提交商店操作。
- 玩家请求地图详情。

```java
FPSMatch.sendToServer(new MyActionPacket(mapName));
```

### `sendTo(Player player, M message)`

根据当前逻辑端自动选择发送方向。服务端玩家会走 S2C，客户端玩家会走 C2S。为了代码可读性，通常更推荐明确使用 `sendToPlayer` 或 `sendToServer`。

## `FPSMCore`

`FPSMCore` 是服务端运行时核心。它保存地图类型注册表、地图实例集合和数据管理器。

### 获取核心实例

```java
FPSMCore core = FPSMCore.getInstance();
```

`getInstance()` 只能在 FPSMatch 初始化后调用。服务端未启动或核心未初始化时会抛出异常。如果你不确定时机，先调用：

```java
if (FPSMCore.initialized()) {
    FPSMCore core = FPSMCore.getInstance();
}
```

### 查询玩家所在地图

```java
BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
```

这个方法只会返回普通参赛玩家所在地图，不包含旁观玩家。如果要把旁观也算作“在地图中”，使用：

```java
BaseMap map = FPSMCore.getInstance().getMapByPlayerWithSpec(player);
```

### 查询地图实例

按类型和名称查询最精确：

```java
BaseMap map = FPSMCore.getInstance().getMapByTypeWithName("example_arena", "test_map");
```

只按名称查询时，多个类型下存在同名地图会有歧义，所以更推荐使用类型和名称。

### 查询位置所在地图

```java
BaseMap map = FPSMCore.getInstance().getMapByPosition(level, blockPos);
```

这个方法会检查 `AreaData`，适合在方块交互、实体事件或区域判定中找到当前坐标属于哪张地图。

### 注册地图类型

通常不直接调用 `FPSMCore.registerGameType`，而是监听 `RegisterFPSMapEvent`。事件会在服务端启动期间由 FPSMatch 发布。

```java
@SubscribeEvent
public static void onMapRegister(RegisterFPSMapEvent event) {
    event.registerGameType(ExampleArenaMap.TYPE, ExampleArenaMap::new);
}
```

## `BaseMap`

`BaseMap` 是玩法地图基类。它封装了对局 tick、胜利判定、队伍、配置、能力、事件发布和客户端同步入口。

### `mapTick()` 做了什么

`mapTick()` 是 FPSMatch 调用的固定流程。子类一般不覆盖它。

它会依次处理：

1. 如果地图已开始，检查 `victoryGoal()`。
2. 调用子类 `tick()`。
3. tick 所有队伍。
4. tick 地图能力。
5. 调用 `syncToClient()`。

因此，玩法逻辑写在 `tick()`，胜利条件写在 `victoryGoal()`，同步逻辑写在 `syncToClient()`。

### `start()`

启动地图并发布 `FPSMapEvent.StartEvent`。该事件可取消。命令 `/fpsm map modify ... debug start` 最终会调用它。

如果你覆盖 `start()`，应保留父类行为：

```java
@Override
public boolean start() {
    if (!super.start()) {
        return false;
    }

    // 只有事件未取消时才执行自定义启动逻辑。
    return true;
}
```

### `victoryGoal()`

抽象方法。每 tick 检查一次。返回 `true` 时会触发 `victory()`。

适合检查：

- 某队分数达到上限。
- 只剩一个队伍存活。
- 目标物被完成。
- 回合时间结束。

### `victory()`

发布 `FPSMapEvent.VictoryEvent`。事件中会生成玩家计分板快照和队伍汇总，适合外部统计、奖励、广播和记录结果。

### `handleDeath(DeathContext context)`

处理对局内死亡。默认实现会把死亡玩家标记为死亡并增加死亡数。自定义玩法通常在调用 `super.handleDeath(context)` 后继续处理击杀分、回合状态或复活。

### `cleanupMap()`

清理地图临时状态前发布 `FPSMapEvent.ClearEvent`。该事件可取消。适合清除掉落物、投掷物、临时实体或临时方块。

### `reset()`

重置地图并发布 `FPSMapEvent.ResetEvent`。通常用于回到未开始状态，清理队伍和能力的临时数据。

### `settings()` 和 `addSetting()`

`Setting<T>` 是地图可配置参数。添加到地图后，可以通过命令读取和修改。

```java
private final Setting<Boolean> allowRespawn = new Setting<>(
        "allowRespawn",
        Codec.BOOL,
        Boolean::parseBoolean,
        true
);

public ExampleArenaMap(ServerLevel level, String name, AreaData area) {
    super(level, name, area);

    // 注册后可通过 settings 命令修改。
    this.addSetting(allowRespawn);
}
```

## `MapTeams`

`MapTeams` 管理一张地图里的队伍和玩家关系。你通常通过 `BaseMap.getMapTeams()` 获取它。

### 获取玩家队伍

```java
ServerTeam team = map.getMapTeams().getTeamByPlayer(player);
```

如果玩家不在普通队伍中，可能返回 `null`。处理玩家事件时需要先判断。

### 获取指定队伍

```java
ServerTeam red = map.getMapTeams().getTeamByName("red");
```

队伍名称来自 `TeamData.of("red", 5)` 的第一个参数。

### 普通队伍和旁观队伍

`getNormalTeams()` 只返回参赛队伍。`getSpectatorTeam()` 返回旁观队伍。FPSMatch 创建 `MapTeams` 时会自动创建 `spectator` 队伍。

### 出生点

`defineSpawnPoint(teamName, spawnPointData)` 给队伍添加出生点。出生点能力会使用这些数据把玩家传送到合适位置。

```java
map.getMapTeams().defineSpawnPoint("red", spawnPointData);
```

## `BaseTeam` 和 `ServerTeam`

`BaseTeam` 是队伍公共基类，`ServerTeam` 是服务端队伍实现。玩法服务端逻辑一般使用 `ServerTeam`。

### 加入和离开

`join(Player player)` 和 `leave(Player player)` 会发布队伍事件。事件被取消时，操作不会成功。

```java
boolean joined = team.join(player);
```

### 队伍分数

```java
team.setScores(team.getScores() + 1);
```

队伍分数适合表示回合分、目标分或模式分。玩家个人击杀、死亡、伤害等数据在 `PlayerData` 中。

### 队伍能力

```java
TeamMoneyCapability cap = team.getCapabilityMap().getCapability(TeamMoneyCapability.class);
```

能力是否存在取决于 `TeamData` 中是否声明，或该能力是否是 original capability。

## Capability API

能力是 FPSMatch 的可复用功能单元。它可以挂在地图或队伍上。

### `MapCapability`

地图能力持有者固定为 `BaseMap`。适合读取地图区域、地图状态、地图队伍和地图配置。

```java
public class ExampleMapCapability extends MapCapability {
    public ExampleMapCapability(BaseMap map) {
        super(map);
    }
}
```

### `TeamCapability`

队伍能力持有者固定为 `BaseTeam`。适合处理队伍经济、出生点、装备、限制等功能。

```java
public class ExampleTeamCapability extends TeamCapability {
    public ExampleTeamCapability(BaseTeam team) {
        super(team);
    }
}
```

### `FPSMCapabilityManager.register(...)`

注册能力类型和工厂。未注册的能力无法通过名称反查，也无法从网络数据恢复。

```java
FPSMCapabilityManager.register(
        FPSMCapabilityManager.CapabilityType.TEAM,
        ExampleTeamCapability.class,
        ExampleTeamCapability::new
);
```

### 内置能力

FPSMatch 默认注册了一些常用能力。它们可以直接用于地图或队伍配置。

地图能力：

- `DemolitionModeCapability`：爆破模式相关地图状态。
- `GameEndTeleportCapability`：游戏结束后的传送逻辑。

队伍能力：

- `SpawnPointCapability`：队伍出生点。
- `ShopCapability`：队伍商店。
- `PauseCapability`：暂停逻辑。
- `StartKitsCapability`：开局装备。
- `CompensationCapability`：经济补偿。
- `TeamSwitchRestrictionCapability`：换队或换边限制。

## `FPSMShop`

`FPSMShop` 是类型化商店。它把“商店分类枚举”和“玩家商店数据”绑定在一起。

### 注册商店类型

```java
FPSMShop.registerShopType("example_shop", ExampleShopType.class);
```

类型 ID 用于网络、配置或命令中识别这个商店类型。

### 创建商店

```java
FPSMShop<ExampleShopType> shop = FPSMShop.create(ExampleShopType.class, "red", 800);
```

第二个参数是商店名，通常可以和队伍名一致。第三个参数是初始金钱。

### 同步商店数据

- `syncShopData()`：同步槽位、购买状态等完整商店数据。
- `syncShopMoneyData()`：只同步金钱，适合频繁更新经济时使用。

## 数据模型

### `AreaData`

保存地图区域的两个角点，并派生出 AABB。常用于判断玩家、实体、方块是否在地图内。

常用判断：

```java
boolean inMap = areaData.isPlayerInArea(player);
boolean blockInMap = areaData.isBlockPosInArea(pos);
boolean entityInMap = areaData.isEntityInArea(entity);
```

### `PlayerData`

保存玩家对局统计，例如分数、击杀、死亡、助攻、伤害、MVP、爆头击杀和存活状态。

回合制玩法需要注意 `_kills`、`_deaths`、`_assists`、`_damage` 这类临时字段。它们表示回合内数据，调用 `saveRoundData()` 后才合并到总数据。

### `Setting<T>`

保存地图配置项。它同时包含：

- 配置名。
- Codec。
- 字符串解析器。
- 默认值。
- 当前值。

这让同一个配置既能保存到 JSON，也能从命令字符串修改，还能通过网络同步。

### `SpawnPointData`

保存出生点维度、坐标、yaw 和 pitch。它不仅保存位置，也保存玩家出生后的朝向。

### `TeamData`

用于定义队伍名称、人数上限和队伍能力列表。`TeamData.of("red", 5)` 会默认加入 `SpawnPointCapability`。

### `DeathContext`

死亡结算上下文。它把死亡玩家、攻击者、伤害源、死亡物品和枪械击杀细节统一放在一个对象里。

使用时要注意：

- `getDeadPlayer()` 一定有值。
- 攻击者可能为空，优先使用 `getAttackerOptional()`。
- 枪杀、爆头、穿墙、穿烟等信息只有在对应事件能提供数据时才可靠。

## 网络包约定

FPSMatch 的 packet 类必须有固定方法签名：

```java
public static void encode(MyPacket packet, FriendlyByteBuf buf)
public static MyPacket decode(FriendlyByteBuf buf)
public void handle(Supplier<NetworkEvent.Context> context)
```

`NetworkPacketRegister` 会在注册时检查这些方法。如果签名不匹配，注册会失败并抛出异常。
