# FPSM 命令使用帮助

FPSM 提供了一系列命令来管理游戏地图、商店、团队等。以下是各个命令的使用说明：

## 基础命令
- `/fpsm save`：保存当前所有地图数据。
- `/fpsm reload`：重新加载监听模块。

## 监听模块命令 example: 主手拿改变的物品，副手拿原先的物品
- `/fpsm listenerModule add changeItemModule <changedCost> <defaultCost>`：添加一个改变物品成本的监听模块。

## 商店命令
- `/fpsm shop <gameType> <mapName> modify set <shopType> <shopSlot> listenerModule add <listenerModule>`：为指定游戏类型和地图的商店添加监听模块。
- `/fpsm shop <gameType> <mapName> modify set <shopType> <shopSlot> listenerModule remove <listenerModule>`：移除指定游戏类型和地图的商店监听模块。
- `/fpsm shop <gameType> <mapName> modify set <shopType> <shopSlot> groupID <groupID>`：修改商店分组ID。
- `/fpsm shop <gameType> <mapName> modify set <shopType> <shopSlot> cost <cost>`：修改商店物品成本。
- `/fpsm shop <gameType> <mapName> modify set <shopType> <shopSlot> item`：修改商店物品，不指定具体值时使用手持物品。
- `/fpsm shop <gameType> <mapName> modify set <shopType> <shopSlot> item <item>`：指定具体物品修改商店物品。
- `/fpsm shop <gameType> <mapName> modify set <shopType> <shopSlot> dummyAmmoAmount <dummyAmmoAmount>`：修改枪械的虚拟弹药数量。

## 地图命令
- `/fpsm map create <gameType> <mapName> from <from> to <to>`：创建一个新地图，不包括出生点。
- `/fpsm map modify <mapName> bombArea add from <from> to <to>`：为地图添加爆炸区域。
- `/fpsm map modify <mapName> debug <action>`：执行地图调试操作，如开始游戏、重置游戏等。
- `/fpsm map modify <mapName> team <teamName> kits <action>`：对团队工具包进行操作，如添加、清除工具包。
- `/fpsm map modify <mapName> team <teamName> kits <action> <item> <amount>`：对团队工具包进行操作，指定具体物品和数量。
- `/fpsm map modify <mapName> spawnpoints <action>`：对地图的出生点进行操作，如添加、清除出生点。
- `/fpsm map modify <mapName> players <targets> <action>`：对地图上的玩家执行操作，如加入或离开团队。

使用AI生成的指令帮助文件 修改日期 2024/12/27日00点28分。