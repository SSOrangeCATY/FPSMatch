# 扩展指南

本文说明 FPSMatch 中最常用的扩展方式。每一节只讲一种扩展点：它解决什么问题、什么时候该用、最小写法是什么。

## 新增玩法地图

当你要做一个新的游戏模式时，应继承 `BaseMap`。`BaseMap` 负责对局的通用流程，你只需要补充玩法规则。

### 什么时候覆盖 `tick()`

`tick()` 适合写每 tick 都要检查的玩法逻辑，例如倒计时、区域检测、目标状态刷新。不要在这里写客户端专用代码，因为它运行在地图服务端逻辑中。

```java
@Override
public void tick() {
    // 每 tick 检查一次自定义状态。
    // victoryGoal() 适合判断是否结束，tick() 适合更新过程状态。
}
```

### 什么时候覆盖 `syncToClient()`

`syncToClient()` 适合同步 HUD、计分板或地图状态。它会在 `mapTick()` 流程中被调用。

```java
@Override
public void syncToClient() {
    // 在这里发送自定义 S2C packet，或者同步地图/队伍能力数据。
}
```

### 什么时候覆盖 `handleDeath()`

当死亡会影响计分、回合、复活或胜负时，覆盖 `handleDeath(DeathContext context)`。

```java
@Override
public void handleDeath(DeathContext context) {
    super.handleDeath(context);

    // 攻击者可能为空，处理前先判断。
    context.getAttackerOptional().ifPresent(attacker -> {
        ServerTeam team = this.getMapTeams().getTeamByPlayer(attacker);
        team.setScores(team.getScores() + 1);
    });
}
```

## 新增地图能力

地图能力用于“挂在整张地图上”的功能。它适合处理结束传送、地图投票、全局目标、地图级状态同步等逻辑。

最小地图能力：

```java
public class RoundTimerCapability extends MapCapability {
    private int ticksLeft = 20 * 120;

    public RoundTimerCapability(BaseMap map) {
        super(map);
    }

    @Override
    public void tick() {
        // map 是当前能力所属地图。
        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }
}
```

注册能力：

```java
FPSMCapabilityManager.register(
        FPSMCapabilityManager.CapabilityType.MAP,
        RoundTimerCapability.class,
        RoundTimerCapability::new
);
```

把能力挂到地图上有两种方式：

- 构造 `BaseMap` 时传入能力列表。
- 在能力工厂中把它声明为 original capability，让 FPSMatch 创建地图时自动加入。

## 新增队伍能力

队伍能力用于“挂在某个队伍上”的功能。它适合处理出生点、商店、队伍经济、换边限制、队伍暂停等逻辑。

最小队伍能力：

```java
public class TeamMoneyCapability extends TeamCapability {
    private int bonusMoney;

    public TeamMoneyCapability(BaseTeam team) {
        super(team);
    }

    public void addBonusMoney(int value) {
        // team 是当前能力所属队伍。
        bonusMoney += value;
    }
}
```

注册能力：

```java
FPSMCapabilityManager.register(
        FPSMCapabilityManager.CapabilityType.TEAM,
        TeamMoneyCapability.class,
        TeamMoneyCapability::new
);
```

给队伍添加能力：

```java
this.addTeam(TeamData.of("red", 5, List.of(TeamMoneyCapability.class)));
```

## 能力生命周期

能力有四个常用生命周期方法。

### `init()`

能力加入 holder 后调用。适合初始化缓存、注册临时数据或执行一次性同步。

### `tick()`

holder 每 tick 更新时调用。地图能力跟随地图 tick，队伍能力跟随队伍 tick。

### `reset()`

地图或队伍重置时调用。适合清空回合内状态，例如计时器、临时积分、临时标记。

### `destroy()`

能力被移除或 holder 销毁时调用。适合释放外部引用或关闭临时资源。

## 能力同步

如果能力需要把完整状态从服务端同步到客户端，实现 `FPSMCapability.CapabilitySynchronizable`。

```java
public class SyncedFlagCapability extends MapCapability implements FPSMCapability.CapabilitySynchronizable {
    private boolean dirty;
    private boolean enabled;

    public SyncedFlagCapability(BaseMap map) {
        super(map);
    }

    @Override
    public boolean isDirty() {
        // 返回 true 时才需要同步。
        return dirty;
    }

    @Override
    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        dirty = false;
    }

    @Override
    public void readFromBuf(FriendlyByteBuf buf) {
        enabled = buf.readBoolean();
    }
}
```

如果能力只想提供自定义同步入口，实现 `FPSMCapability.DataSynchronizable`，然后在 `sync()` 或 `sync(Player player)` 中发送自己的 packet。

## 能力持久化

实现 `FPSMCapability.Savable<T>` 后，能力可以把自己的状态写入 JSON。`codec()` 决定数据如何编码，`read()` 把运行时状态导出为可保存值，`write(T value)` 把保存值写回运行时状态。

```java
public class SavedCounterCapability extends MapCapability implements FPSMCapability.Savable<Integer> {
    private int counter;

    public SavedCounterCapability(BaseMap map) {
        super(map);
    }

    @Override
    public String getName() {
        return "savedCounter";
    }

    @Override
    public Codec<Integer> codec() {
        return Codec.INT;
    }

    @Override
    public void write(Integer value) {
        counter = value;
    }

    @Override
    public Integer read() {
        return counter;
    }
}
```

## 新增命令

如果命令是整个 FPSMatch 的功能入口，监听 `RegisterFPSMCommandEvent`。如果命令只服务于某个能力，优先做成能力命令。

```java
@SubscribeEvent
public static void onCommandRegister(RegisterFPSMCommandEvent event) {
    event.addChild(Commands.literal("my_command")
            .executes(context -> {
                // 这里运行服务端命令逻辑。
                context.getSource().sendSuccess(() -> Component.literal("ok"), false);
                return 1;
            }));

    // 注册帮助文本后，/fpsm help 可以显示该命令。
    event.registerHelp("fpsm my_command", "commands.my_mod.my_command.help");
}
```

## 新增网络包

FPSMatch 使用 `NetworkPacketRegister` 注册 packet。每个 packet 类都必须提供三个方法：

```java
public static void encode(MyPacket packet, FriendlyByteBuf buf)
public static MyPacket decode(FriendlyByteBuf buf)
public void handle(Supplier<NetworkEvent.Context> context)
```

服务端发给客户端：

```java
FPSMatch.sendToPlayer(serverPlayer, packet);
```

客户端发给服务端：

```java
FPSMatch.sendToServer(packet);
```

S2C packet 不应该直接引用 client-only 类型。客户端执行逻辑应通过 `ClientPacketExecutor` 和 `ClientPacketRegistry` 分发，避免服务端加载客户端类。

## 新增商店类型

商店类型需要实现 `INamedType`。它描述一个商店分类：名称、槽位数量、是否解锁掉落物、默认槽位列表。

```java
public enum ExampleShopType implements INamedType {
    RIFLE;

    @Override
    public int slotCount() {
        // 这个分类显示多少个槽位。
        return 9;
    }

    @Override
    public boolean dorpUnlock() {
        // 返回 true 时，掉落物相关解锁逻辑由该分类启用。
        return false;
    }

    @Override
    public ArrayList<ShopSlot> defaultSlots() {
        // 返回该分类默认出售物品。
        return new ArrayList<>();
    }
}
```

注册商店类型：

```java
FPSMShop.registerShopType("example_shop", ExampleShopType.class);
```

创建商店实例：

```java
FPSMShop<ExampleShopType> shop = FPSMShop.create(ExampleShopType.class, "red", 800);
```

## 新增持久化数据

如果你的地图或系统需要随存档保存，监听 `RegisterFPSMSaveDataEvent`。FPSMatch 会在服务端启动时读取，在服务端停止时保存。

```java
@SubscribeEvent
public static void onDataRegister(RegisterFPSMSaveDataEvent event) {
    event.registerData(MyData.class, "MyData",
            new SaveHolder.Builder<>(MyData.CODEC)
                    .withLoadHandler(MyData::load)
                    .withSaveHandler(MyData::save)
                    .build()
    );
}
```

`SaveHolder` 的 `Codec` 定义文件格式；load handler 负责把文件数据恢复到运行时；save handler 负责把运行时数据写入数据管理器。
