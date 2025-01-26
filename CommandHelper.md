# FPSM 命令帮助

## 1. /fpsm loadOld
**描述:** 加载旧的地图数据。
**参数:** 无
**示例:** `/fpsm loadOld`
**说明:** 该命令会从存档中加载旧的地图数据，并将其注册到游戏中。

## 2. /fpsm save
**描述:** 保存当前的游戏数据。
**参数:** 无
**示例:** `/fpsm save`
**说明:** 该命令会将当前的游戏数据保存到存档中。

## 3. /fpsm sync
**描述:** 同步商店数据。
**参数:** 无
**示例:** `/fpsm sync`
**说明:** 该命令会同步所有地图的商店数据，确保数据的一致性。

## 4. /fpsm reload
**描述:** 重新加载游戏数据。
**参数:** ���
**示例:** `/fpsm reload`
**说明:** 该命令会重新加载游戏数据，适用于数据更新后需要重新加载的情况。

## 5. /fpsm listenerModule
**描述:** 管理监听器模块。
**参数:**
- `add changeItemModule <changedCost> <defaultCost>`: 添加一个改变物品模块的监听器。
   - `<changedCost>`: 改变后的物品成本。
   - `<defaultCost>`: 默认的物品成本。
     **示例:** `/fpsm listenerModule add changeItemModule 10 5`
     **说明:** 该命令会添加一个监听器，用于改变物品的成本。玩家的主手物品将被设置为改变后的物品，副手物品将被设置为默认物品。

## 6. /fpsm shop
**描述:** 管理商店。
**参数:**
- `<gameType>`: 游戏类型。
- `<mapName>`: 地图名称。
- `<shopName>`: 商店名称。
- `<shopType>`: 商店类型。
- `<shopSlot>`: 商店槽位。
  **子命令:**
- `modify set <shopName> <shopType> <shopSlot> listenerModule add <listenerModule>`: 为商店添加一个监听器模块。
   - `<listenerModule>`: 监听器模块名称。
     **示例:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 listenerModule add module1`
     **说明:** 该命令会为指定的商店槽位添加一个监听器模块。
- `modify set <shopName> <shopType> <shopSlot> listenerModule remove <listenerModule>`: 为商店移除一个监听器模块。
   - `<listenerModule>`: 监听器模块名称。
     **示例:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 listenerModule remove module1`
     **说明:** 该命令会为指定的商店槽位移除一个监听器模块。
- `modify set <shopName> <shopType> <shopSlot> groupID <groupID>`: 修改商店槽位的组ID。
   - `<groupID>`: 组ID。
     **示例:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 groupID 2`
     **说明:** 该命令会修改指定商店槽位的组ID。
- `modify set <shopName> <shopType> <shopSlot> cost <cost>`: 修改商店槽位的成本。
   - `<cost>`: 成本。
     **示例:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 cost 10`
     **说明:** 该命令会修改指定商店槽位的成本。
- `modify set <shopName> <shopType> <shopSlot> item <item>`: 修改商店槽位的物品。
   - `<item>`: 物品。
     **示例:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 item diamond_sword`
     **说明:** 该命令会修改指定商店槽位的物品。
- `modify set <shopName> <shopType> <shopSlot> dummyAmmoAmount <dummyAmmoAmount>`: 修改商店槽位的虚拟弹药数量。
   - `<dummyAmmoAmount>`: 虚拟弹药数量。
     **示例:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 dummyAmmoAmount 50`
     **说明:** 该命令会修改指定商店槽位的虚拟弹药数量。

## 7. /fpsm map
**描述:** 管理地图。
**参数:**
- `<gameType>`: 游戏类型。
- `<mapName>`: 地图名称。
- `<from>`: 区域的起始坐标。
- `<to>`: 区域的结束坐标。
- `<point>`: 传送点坐标。
- `<teamName>`: 团队名称。
- `<action>`: 调试操作，如 start、reset、newRound、cleanup、switch。
  **子命令:**
- `create <gameType> <mapName> from <from> to <to>`: 创建一个新的地图。
  **示例:** `/fpsm map create fpsm map1 from 0 0 0 to 100 100 100`
  **说明:** 该命令会创建一个新的地图，区域由 `<from>` 和 `<to>` 两个对角点形成的正方形区域定义。
- `modify <mapName> matchEndTeleportPoint <point>`: 修改地图的匹配结束传送点。
  **示例:** `/fpsm map modify map1 matchEndTeleportPoint 50 50 50`
  **说明:** 该命令会修改指定地图的匹配结束传送点。
- `modify <mapName> bombArea add from <from> to <to>`: 为地图添加一个炸弹区域。
  **示例:** `/fpsm map modify map1 bombArea add from 0 0 0 to 10 10 10`
  **说明:** 该命令会为指定地图添加一个炸弹区域，区域由 `<from>` 和 `<to>` 两个对角点形成的正方形区域定义。
- `modify <mapName> debug <action>`: 对地图进行调试操作。
  **示例:** `/fpsm map modify map1 debug start`
  **说明:** 该命令会对指定地图进行调试操作，具体操作根据 `<action>` 参数决定。
- `modify <mapName> join`: 加入地图。
  **示例:** `/fpsm map modify map1 join`
  **说明:** 该命令会将玩家加入指定的地图。
- `modify <mapName> team <teamName> kits <action> <item>`: 修改团队的起始装备。
   - `<action>`: 操作类型，如 add、clear、list。
   - `<item>`: 物品。
     **示例:** `/fpsm map modify map1 team team1 kits add diamond_sword`
     **说明:** 该命令会为指定团队添加起始装备。