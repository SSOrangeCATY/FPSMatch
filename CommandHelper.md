以下是这个Minecraft模组（FPSM，即First Person Shooter Match）的命令使用帮助文档：

### 基本命令结构
```
/fpsm <子命令> [参数] [...]
```

### 子命令列表

#### 1. shop
用于管理商店相关的命令。

```
/fpsm shop <gameType> <mapName> modify set <shopType> <shopSlot> <cost>
```
- `<gameType>`: 游戏类型
- `<mapName>`: 地图名称
- `<shopType>`: 商店物品类型
- `<shopSlot>`: 商店槽位（1-5）
- `<cost>`: 物品成本

#### 2. map
用于地图相关的管理命令。

##### 创建地图
```
/fpsm map create <gameType> <mapName>
```
- `<gameType>`: 游戏类型
- `<mapName>`: 地图名称

##### 修改地图
```
/fpsm map modify <mapName> <子命令> [...]
```
- `<mapName>`: 地图名称

###### 添加炸弹区域
```
/fpsm map modify <mapName> bombArea add <from> <to>
```
- `<from>`: 炸弹区域起始坐标
- `<to>`: 炸弹区域结束坐标

###### 调试操作
```
/fpsm map modify <mapName> debug <action>
```
- `<action>`: 调试动作（start, reset, newround, cleanup, switch）

###### 团队操作
```
/fpsm map modify <mapName> team <teamName> <子命令> [...]
```
- `<teamName>`: 团队名称

####### 团队成员加入/离开
```
/fpsm map modify <mapName> team <teamName> players <targets> <action>
```
- `<targets>`: 玩家列表
- `<action>`: 动作（join, leave）

####### 设置出生点
```
/fpsm map modify <mapName> team <teamName> spawnpoints <action>
```
- `<action>`: 动作（add, clear, clearall）

### 注意事项
- 所有命令都需要相应的权限等级（通常是2级或以上）。
- `<gameType>`, `<mapName>`, `<shopType>`, `<teamName>`, `<action>` 等参数支持自动补全，输入时可以按Tab键自动补全。
- `<from>` 和 `<to>` 参数需要指定具体的坐标，格式为 `x y z`。
- `<cost>`, `<shopSlot>` 等参数需要指定具体的数字。
- `<action>` 参数根据上下文有不同的选项，具体参考每个子命令的说明。

以上是FPSM模组命令的基本使用帮助文档，玩家可以根据这个文档来使用模组提供的命令。
使用AI生成，具体功能随版本更新而变化，本文件可能会在后续更新中进行修改。
