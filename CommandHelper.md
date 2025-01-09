### FPSM命令教程

#### 1. 命令概述
FPSM（FPS Match）命令用于管理FPS游戏模式的各种设置和操作，包括地图创建、修改，商店设置，团队管理等。该命令的主命令为`/fpsm`，需要至少2级权限才能使用。

#### 2. 地图相关操作
- **创建地图**
    - `/fpsm map create <gameType> <mapName> <from> <to>`
        - `gameType`：游戏类型，如`shop`、`blast`等.
        - `mapName`：地图名称.
        - `from`和`to`：分别指定地图的起始和结束坐标.
        - 示例：`/fpsm map create shop MyMap 100 100 100 200 200 200`，创建一个名为“MyMap”的商店模式地图，范围从坐标(100,100,100)到(200,200,200).
- **修改地图**
    - **设置比赛结束传送点**
        - `/fpsm map modify <mapName> matchEndTeleportPoint <point>`
            - 示例：`/fpsm map modify MyMap matchEndTeleportPoint 150 150 150`，设置“MyMap”地图的比赛结束传送点为坐标(150,150,150).
    - **添加炸弹区域**
        - `/fpsm map modify <mapName> bombArea add <from> <to>`
            - 示例：`/fpsm map modify MyMap bombArea add 120 120 120 180 180 180`，在“MyMap”地图添加一个炸弹区域，范围从坐标(120,120,120)到(180,180,180).
    - **调试地图**
        - `/fpsm map modify <mapName> debug <action>`
            - `action`：调试操作，包括`start`、`reset`、`newRound`、`cleanup`、`switch`.
            - 示例：`/fpsm map modify MyMap debug start`，开始“MyMap”地图的游戏调试.

#### 3. 商店相关操作
- **修改商店**
    - `/fpsm shop <gameType> <mapName> modify set <shopName> <shopType> <shopSlot> <action>`
        - `gameType`和`mapName`：指定游戏类型和地图名称.
        - `shopName`：商店名称.
        - `shopType`：商店类型，如`primary`、`secondary`等.
        - `shopSlot`：商店槽位，范围为1-5.
        - `action`：修改操作，包括`listenerModule`、`groupID`、`cost`、`item`、`dummyAmmoAmount`.
        - 示例：`/fpsm shop shop MyMap modify set MyShop primary 1 listenerModule add MyListener`，在“MyMap”地图的“MyShop”商店的主武器槽位1添加“MyListener”监听模块.
- **同步商店数据**
    - `/fpsm sync`
        - 示例：`/fpsm sync`，同步所有地图的商店数据.

#### 4. 团队相关操作
- **管理团队成员**
    - `/fpsm map modify <mapName> team <teamName> <action> <targets>`
        - `action`：操作类型，包括`join`、`leave`.
        - `targets`：指定玩家，使用`@p`、`@a`等选择器或玩家名称.
        - 示例：`/fpsm map modify MyMap team TeamA join @p`，让当前玩家加入“MyMap”地图的“TeamA”团队.
- **设置团队初始装备**
    - `/fpsm map modify <mapName> team <teamName> kits <action> [item] [amount]`
        - `action`：操作类型，包括`add`、`clear`.
        - `item`和`amount`：指定物品和数量，仅在`add`操作时需要.
        - 示例：`/fpsm map modify MyMap team TeamA kits add`，将玩家当前手持物品添加到“MyMap”地图“TeamA”团队的初始装备中.

#### 5. 其他操作
- **保存数据**
    - `/fpsm save`
        - 示例：`/fpsm save`，保存所有地图和监听模块的数据.
- **重新加载数据**
    - `/fpsm reload`
        - 示例：`/fpsm reload`，重新加载监听模块的数据.
- **添加监听模块**
    - `/fpsm listenerModule add changeItemModule <changedCost> <defaultCost>`
        - 示例：`/fpsm listenerModule add changeItemModule 100 50`，添加一个改变物品模块，改变后的成本为100，原始成本为50.

使用AI生成的指令帮助 2025/1/9日更新