# 事件参考

FPSMatch 通过 Forge EventBus 暴露扩展点。事件适合做跨模块监听，例如统计、奖励、限制加入、修改商店初始数据、注册地图类型等。

## 注册事件

注册事件通常在服务端启动或命令注册阶段触发，用于把外部模组能力接入 FPSMatch。

### `RegisterFPSMapEvent`

用于注册地图类型。没有注册的地图类型不能被 `/fpsm map create` 创建。

```java
@SubscribeEvent
public static void onMapRegister(RegisterFPSMapEvent event) {
    // 第一个参数是 gameType，第二个参数是 BaseMap 构造器。
    event.registerGameType(ExampleArenaMap.TYPE, ExampleArenaMap::new);
}
```

### `RegisterFPSMSaveDataEvent`

用于注册需要随存档保存的数据。FPSMatch 会在服务端启动时读取，在服务端停止时保存。

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

### `RegisterFPSMCommandEvent`

用于向 `/fpsm` 命令树添加子命令，或为命令注册帮助文本。

适合用于：

- 添加全局管理命令。
- 添加调试命令。
- 把外部系统挂到 `/fpsm` 下。

### `RegisterListenerModuleEvent`

用于注册商店监听模块。商店槽位可以引用监听模块来处理购买、返还或限制逻辑。

## 地图生命周期事件

地图生命周期事件都继承 `FPSMapEvent`，可以通过 `getMap()` 获取当前地图。

### `StartEvent`

`BaseMap.start()` 时发布。可取消。

用途：

- 阻止不满足条件的地图启动。
- 在启动前检查人数、配置或资源状态。
- 向外部系统广播地图即将开始。

### `VictoryEvent`

`BaseMap.victory()` 时发布。不可取消。

用途：

- 记录比赛结果。
- 发放奖励。
- 生成结算面板。
- 把统计数据发送到外部系统。

`VictoryEvent` 会提供玩家计分板快照和队伍汇总。玩家快照包含分数、击杀、死亡、助攻、伤害和爆头率。队伍汇总包含队伍分、玩家数、总击杀、总死亡、总助攻和总伤害。

### `ClearEvent`

`BaseMap.cleanupMap()` 时发布。可取消。

用途：

- 阻止某些情况下的地图清理。
- 在清理前保存临时数据。
- 扩展清理额外实体或方块。

### `ResetEvent`

`BaseMap.reset()` 时发布。不可取消。

用途：

- 清空外部模块缓存。
- 重置 HUD 或统计状态。
- 通知其他系统地图已回到初始状态。

### `ReloadEvent` 和 `LoadEvent`

用于地图数据重载和加载阶段。`ReloadEvent` 可取消，`LoadEvent` 不可取消。

## 玩家对局事件

玩家事件继承 `FPSMapEvent.PlayerEvent`。可以通过 `getMap()` 获取地图，通过 `getPlayer()` 获取玩家。

### `JoinEvent`

玩家加入地图时发布。可取消。

用途：

- 限制玩家加入条件。
- 检查权限、人数或队伍状态。
- 初始化玩家对局数据。

### `LeaveEvent`

玩家离开地图时发布。可取消。

用途：

- 阻止某些状态下离开。
- 保存玩家临时数据。
- 广播玩家离开。

### `HurtEvent`

对局内玩家受伤时发布。可取消。

用途：

- 阻止友伤。
- 修改伤害规则。
- 统计伤害来源。

### `DeathEvent`

对局内玩家死亡时发布。可取消。

用途：

- 阻止死亡结算。
- 改写复活或淘汰规则。
- 接入自定义死亡惩罚。

### `KillRecordEvent`

击杀记录写入前发布。可取消。

用途：

- 阻止某些击杀计入统计。
- 过滤环境击杀或特殊击杀。
- 修改额外击杀记录逻辑。

### `KillEvent`

击杀完成后发布。不可取消。

用途：

- 发放击杀奖励。
- 播放击杀提示。
- 更新外部排行榜。

### 登录、拾取、丢弃和聊天事件

FPSMatch 还提供玩家上线、下线、拾取、丢弃和聊天相关事件。它们适合限制对局内行为，例如禁止丢弃物品、拦截地图内聊天、处理重连状态。

## 死亡管线

FPSMatch 会接管对局内死亡处理。它会取消原版死亡流程，把死亡上下文延迟到 tick 末尾统一结算。

这样做的目的：

- 让枪械击杀、爆头、穿墙、穿烟等信息有机会补齐。
- 避免多个事件顺序不稳定导致重复结算。
- 让 `DeathContext` 成为统一的死亡数据入口。

开发者通常不需要直接接触死亡管线，只需要监听玩家死亡事件或覆盖 `BaseMap.handleDeath(DeathContext context)`。

## 队伍事件

### `FPSMTeamEvent.JoinEvent`

玩家加入队伍时发布。可取消。

用途：

- 限制指定玩家进入某队。
- 实现队伍平衡规则。
- 同步外部队伍状态。

### `FPSMTeamEvent.LeaveEvent`

玩家离开队伍时发布。可取消。

用途：

- 阻止回合中离队。
- 清理玩家队伍临时数据。
- 通知外部系统队伍变化。

## 商店事件

### `FPSMShopEvent.DataInit`

玩家商店数据初始化前发布。可修改初始金钱。

```java
@SubscribeEvent
public static void onShopDataInit(FPSMShopEvent.DataInit event) {
    // 给新初始化的商店数据设置初始金钱。
    event.setMoney(1000);
}
```

用途：

- 根据地图规则调整初始金钱。
- 根据玩家状态调整经济。
- 读取默认槽位并补充额外初始化逻辑。

## KubeJS 事件桥

安装 KubeJS 时，FPSMatch 会暴露 `FPSMatchEvents` 事件组。脚本可以监听地图开始、胜利、清理、重置、玩家加入、玩家离开、玩家受伤、玩家死亡、玩家击杀、玩家上下线、队伍加入和队伍离开等事件。

如果逻辑需要 Java 类型、能力系统或复杂状态，推荐写 Forge 模组事件监听；如果只是做简单脚本联动，可以使用 KubeJS 事件桥。

## Bukkit 事件桥

在支持 Bukkit 事件的环境中，FPSMatch 会把部分 Forge 事件桥接为 Bukkit 事件。

当前桥接内容包括：

- 玩家在地图中完成击杀。
- 地图产生胜利结果。

这适合让服务端插件监听 FPSMatch 的比赛结果或击杀事件。
