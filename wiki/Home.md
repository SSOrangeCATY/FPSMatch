# FPSMatch Developer Wiki

FPSMatch 是 Minecraft 1.20.1 Forge 的 FPS 对局框架库。它负责提供地图、队伍、对局生命周期、能力、商店、命令、事件和网络同步等基础设施。玩法模组通常不需要重写这些基础系统，只需要在 FPSMatch 提供的扩展点上注册自己的地图类型、能力或事件逻辑。

## 阅读顺序

1. [从零开始：开发者教程](Developer-Getting-Started)
2. [扩展指南](Extension-Guide)
3. [API 与接口文档](API-Reference)
4. [命令与地图制作](Commands-and-Map-Tools)
5. [事件参考](Events)

## 你需要先理解的概念

### 地图类型

地图类型是一个字符串 ID，例如 `example_arena`。它由 `RegisterFPSMapEvent` 注册，并对应一个 `BaseMap` 构造器。执行 `/fpsm map create` 时，FPSMatch 会根据这个 ID 创建地图实例。

### 地图实例

地图实例是一个实际运行的对局。它有名称、区域、队伍、配置项、能力和对局状态。开发者通常通过继承 `BaseMap` 实现自己的玩法规则。

### 队伍

每个地图实例都有一个 `MapTeams`。它管理普通队伍、旁观队伍、队伍玩家、出生点和队伍能力。

### 能力

能力是可复用功能模块。地图能力挂在 `BaseMap` 上，队伍能力挂在 `BaseTeam` 上。出生点、商店、暂停、爆破模式等都适合用能力实现。

### 事件

FPSMatch 通过 Forge EventBus 暴露注册、地图生命周期、玩家对局、队伍、商店等事件。外部模组可以监听这些事件来接入逻辑，而不需要修改 FPSMatch 源码。

## 适用版本

- Minecraft：1.20.1
- Forge：47.3.11
- Java：21
- FPSMatch：1.2.5 或兼容的 1.3.x 开发版本

## 最小接入流程

1. 在你的 Forge 模组中依赖 FPSMatch。
2. 创建一个继承 `BaseMap` 的地图类。
3. 在构造函数中添加队伍。
4. 实现 `getGameType()` 和 `victoryGoal()`。
5. 监听 `RegisterFPSMapEvent` 注册地图类型。
6. 进游戏后用 `/fpsm map create` 创建地图实例。
7. 用 `/fpsm map modify` 调试队伍、配置和对局流程。
