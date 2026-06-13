# 命令与地图制作

FPSMatch 的主命令是 `/fpsm`，默认需要权限等级 2。命令主要用于创建地图实例、调试地图流程、管理队伍和修改地图配置。

## 创建地图实例

```text
/fpsm map create <gameType> <mapName> <from> <to>
```

这个命令会根据 `gameType` 找到通过 `RegisterFPSMapEvent` 注册的地图构造器，然后用 `mapName` 和 `AreaData` 创建地图实例。

参数说明：

- `<gameType>`：地图类型 ID，例如 `example_arena`。
- `<mapName>`：地图实例名称。
- `<from>`：地图区域第一个角点。
- `<to>`：地图区域第二个角点。

示例：

```text
/fpsm map create example_arena test_map ~ ~ ~ ~30 ~20 ~30
```

## 启动和调试地图

```text
/fpsm map modify <gameType> <mapName> debug <action>
```

### `start`

调用 `BaseMap.start()`。如果 `FPSMapEvent.StartEvent` 没有被取消，地图进入启动流程。

```text
/fpsm map modify example_arena test_map debug start
```

### `reset`

调用 `BaseMap.reset()`。适合测试地图能否恢复到初始状态。

```text
/fpsm map modify example_arena test_map debug reset
```

### `new_round`

调用 `BaseMap.startNewRound()`。适合回合制玩法测试新回合逻辑。

```text
/fpsm map modify example_arena test_map debug new_round
```

### `cleanup`

调用 `BaseMap.cleanupMap()`。适合测试清理掉落物、投掷物、临时实体或临时方块的逻辑。

```text
/fpsm map modify example_arena test_map debug cleanup
```

### `switch`

切换地图调试显示。适合查看地图区域、状态或调试信息。

```text
/fpsm map modify example_arena test_map debug switch
```

## 让玩家加入或离开地图

```text
/fpsm map modify <gameType> <mapName> team join [targets]
/fpsm map modify <gameType> <mapName> team leave [targets]
```

`targets` 是 Minecraft 实体选择器。如果不传，通常表示命令执行者。

示例：

```text
/fpsm map modify example_arena test_map team join @s
```

玩家加入时，FPSMatch 会尝试分配可加入队伍。如果地图不允许中途加入、队伍已满或事件被取消，加入会失败。

## 操作指定队伍

```text
/fpsm map modify <gameType> <mapName> team teams <teamName> players <targets> <action>
```

这个分支用于对指定队伍执行玩家操作。`teamName` 是地图中注册的队伍名，例如 `red`、`blue` 或 `spectator`。

旁观队伍也按普通队伍路径访问：

```text
/fpsm map modify example_arena test_map team teams spectator players @s <action>
```

## 修改地图配置

地图配置来自 `BaseMap.addSetting(...)`。

### 查看配置列表

```text
/fpsm map modify <gameType> <mapName> settings list
```

用于确认当前地图有哪些配置项。

### 读取配置

```text
/fpsm map modify <gameType> <mapName> settings get <setting>
```

示例：

```text
/fpsm map modify example_arena test_map settings get allowJoinInProgress
```

### 修改配置

```text
/fpsm map modify <gameType> <mapName> settings set <setting> <value>
```

示例：

```text
/fpsm map modify example_arena test_map settings set allowJoinInProgress false
```

`value` 会交给 `Setting<T>` 中的 parser 解析。解析失败时，配置不会成功修改。

### 保存和读取配置

```text
/fpsm map modify <gameType> <mapName> settings save
/fpsm map modify <gameType> <mapName> settings load
```

`save` 把当前配置写入持久化数据。`load` 从已保存数据恢复配置。

## 能力命令

能力可以提供自己的命令分支。FPSMatch 会根据能力类型把它挂到不同位置。

### 地图能力命令

```text
/fpsm map modify <gameType> <mapName> capability <capabilityCommand>
```

适合操作地图级能力，例如地图倒计时、全局规则、结束传送等。

### 队伍能力命令

```text
/fpsm map modify <gameType> <mapName> team teams <teamName> capability <capabilityCommand>
```

适合操作队伍级能力，例如出生点、商店、暂停、队伍经济等。

## 推荐地图制作顺序

1. 注册地图类型。
2. 用 `/fpsm map create` 创建地图区域。
3. 用 `team join` 确认玩家能进入地图。
4. 用 `settings list` 检查配置项是否注册成功。
5. 用 `debug start` 测试启动流程。
6. 用 `debug reset` 测试重置流程。
7. 如果是回合制玩法，用 `debug new_round` 测试新回合流程。
8. 如果实现了清理逻辑，用 `debug cleanup` 单独测试清理行为。
