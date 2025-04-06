### FPSM 指令帮助文档

语法树：
```plaintext
fpsm
├── mvp <targets> <sound>
├── save
├── sync
├── reload
├── listenerModule add changeItemModule <changedCost> <defaultCost>
├── shop <gameType> <mapName>
│   ├── modify set <shopName> <shopType> <shopSlot>
│   │   ├── listenerModule add <listenerModule>
│   │   ├── listenerModule remove <listenerModule>
│   │   ├── groupID <groupID>
│   │   ├── cost <cost>
│   │   ├── item <item>
│   │   ├── dummyAmmoAmount <dummyAmmoAmount>
├── map
│   ├── create <gameType> <mapName> <from> <to>
│   ├── modify <mapName>
│   │   ├── matchEndTeleportPoint <point>
│   │   ├── bombArea add <from> <to>
│   │   ├── debug <action>
│   │   ├── team
│   │   │   ├── join <targets>
│   │   │   ├── leave <targets>
│   │   │   ├── teams
│   │   │   │   ├── spectator players <targets> <action>
│   │   │   │   ├── <teamName> kits <action>
│   │   │   │   │   ├── dummyAmmoAmount <dummyAmmoAmount>
│   │   │   │   │   ├── item <item> <amount>
│   │   │   │   ├── spawnpoints <action> <from> <to>
│   │   │   │   ├── players <targets> <action>
```

## 1. /fpsm loadOld
**描述：** 加载旧地图数据。  
**参数：** 无  
**示例：** `/fpsm loadOld`  
**说明：** 该命令从存档中加载旧的地图数据并将其注册到游戏中。

## 2. /fpsm save
**描述：** 保存当前游戏数据。  
**参数：** 无  
**示例：** `/fpsm save`  
**说明：** 该命令将当前的游戏数据保存到存档中。

## 3. /fpsm sync
**描述：** 同步商店数据。  
**参数：** 无  
**示例：** `/fpsm sync`  
**说明：** 该命令将所有地图的商店数据同步，确保数据一致性。

## 4. /fpsm reload
**描述：** 重新加载游戏数据。  
**参数：** 无  
**示例：** `/fpsm reload`  
**说明：** 该命令重新加载游戏数据，适用于数据更新后重新加载。

## 5. /fpsm listenerModule
**描述：** 管理监听模块。  
**参数：**
- `add changeItemModule <changedCost> <defaultCost>`: 添加监听模块以改变物品成本。
    - `<changedCost>`: 变更后的物品成本。
    - `<defaultCost>`: 默认物品成本。  
      **示例：** `/fpsm listenerModule add changeItemModule 10 5`  
      **说明：** 该命令添加一个监听器来改变物品的成本。主手物品将设置为已更改的物品，副手物品将设置为默认物品。

## 6. /fpsm shop
**描述：** 管理商店。  
**参数：**
- `<gameType>`: 游戏类型。
- `<mapName>`: 地图名称。
- `<shopName>`: 商店名称。
- `<shopType>`: 商店类型。
- `<shopSlot>`: 商店槽位。  
  **子命令：**
- `modify set <shopName> <shopType> <shopSlot> listenerModule add <listenerModule>`: 向商店添加监听模块。
    - `<listenerModule>`: 监听模块名称。  
      **示例：** `/fpsm shop fpsm map1 modify set shop1 weapon 1 listenerModule add module1`  
      **说明：** 该命令向指定的商店槽位添加一个监听模块。
- `modify set <shopName> <shopType> <shopSlot> listenerModule remove <listenerModule>`: 从商店移除监听模块。
    - `<listenerModule>`: 监听模块名称。  
      **示例：** `/fpsm shop fpsm map1 modify set shop1 weapon 1 listenerModule remove module1`  
      **说明：** 该命令从指定的商店槽位移除一个监听模块。
- `modify set <shopName> <shopType> <shopSlot> groupID <groupID>`: 修改商店槽位的组ID。
    - `<groupID>`: 组ID。  
      **示例：** `/fpsm shop fpsm map1 modify set shop1 weapon 1 groupID 2`  
      **说明：** 该命令修改指定商店槽位的组ID。
- `modify set <shopName> <shopType> <shopSlot> cost <cost>`: 修改商店槽位的成本。
    - `<cost>`: 成本。  
      **示例：** `/fpsm shop fpsm map1 modify set shop1 weapon 1 cost 10`  
      **说明：** 该命令修改指定商店槽位的成本。
- `modify set <shopName> <shopType> <shopSlot> item <item>`: 修改商店槽位的物品。
    - `<item>`: 物品。  
      **示例：** `/fpsm shop fpsm map1 modify set shop1 weapon 1 item diamond_sword`  
      **说明：** 该命令修改指定商店槽位的物品。
- `modify set <shopName> <shopType> <shopSlot> dummyAmmoAmount <dummyAmmoAmount>`: 修改商店槽位的虚拟弹药数量。
    - `<dummyAmmoAmount>`: 虚拟弹药数量。  
      **示例：** `/fpsm shop fpsm map1 modify set shop1 weapon 1 dummyAmmoAmount 50`  
      **说明：** 该命令修改指定商店槽位的虚拟弹药数量。

## 7. /fpsm map
**描述：** 管理地图。  
**参数：**
- `<gameType>`: 游戏类型。
- `<mapName>`: 地图名称。
- `<from>`: 区域的起始坐标。
- `<to>`: 区域的结束坐标。
- `<point>`: 传送点坐标。
- `<teamName>`: 队伍名称。
- `<action>`: 调试动作，如开始、重置、新回合、清理、切换等。  
  **子命令：**
- `create <gameType> <mapName> from <from> to <to>`: 创建新地图。  
  **示例：** `/fpsm map create fpsm map1 from 0 0 0 to 100 100 100`  
  **说明：** 该命令创建一个新地图，区域由两个对角坐标 `<from>` 和 `<to>` 定义。
- `modify <mapName> matchEndTeleportPoint <point>`: 修改地图的比赛结束传送点。  
  **示例：** `/fpsm map modify map1 matchEndTeleportPoint 50 50 50`  
  **说明：** 该命令修改指定地图的比赛结束传送点。
- `modify <mapName> bombArea add from <from> to <to>`: 向地图添加炸弹区。  
  **示例：** `/fpsm map modify map1 bombArea add from 0 0 0 to 10 10 10`  
  **说明：** 该命令向指定地图添加一个炸弹区，区域由两个对角坐标 `<from>` 和 `<to>` 定义。
- `modify <mapName> debug <action>`: 执行地图的调试动作。  
  **示例：** `/fpsm map modify map1 debug start`  
  **说明：** 该命令在指定地图上执行调试动作，具体动作由 `<action>` 参数确定。
- `modify <mapName> join`: 加入地图。  
  **示例：** `/fpsm map modify map1 join`  
  **说明：** 该命令将玩家加入指定地图。
- `modify <mapName> team <teamName> kits <action> <item>`: 修改队伍的起始装备。
    - `<action>`: 动作类型，如添加、清除、列出。
    - `<item>`: 物品。  
      **示例：** `/fpsm map modify map1 team team1 kits add diamond_sword`  
      **说明：** 该命令向指定队伍添加起始装备。
- `modify <mapName> team <teamName> teams <action>`
    - `<action>`: 动作类型，如添加、清除、清除所有、设置。
        - `set`: 该命令在指定的地图区域内为指定队伍设置重生点。你可以使用两个对角坐标来指定矩形区域。默认情况下，区域内的所有重生点会基于执行指令时玩家的位置来保存 `y` 坐标，并且重生点的朝向将与玩家的朝向一致。需要提供额外的 `<from> <to>` 二维向量（不含y轴）。