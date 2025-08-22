### 🎯 FPSM 指令帮助文档 (v1.2.3.14)

#### 语法树概览：
```plaintext
/fpsm
├── save
├── sync
├── reload
├── listenerModule add changeItemModule <changedCost> <defaultCost>
├── shop <gameType> <mapName>
│   └── modify set <shopName> <shopType> <shopSlot>
│       ├── listenerModule add <listenerModule>
│       ├── listenerModule remove <listenerModule>
│       ├── groupID <groupID>
│       ├── cost <cost>
│       ├── item [<item>]
│       └── dummyAmmoAmount <dummyAmmoAmount>
└── map
    ├── create <gameType> <mapName> <from> <to>
    └── modify <gameType> <mapName>
        ├── matchEndTeleportPoint <point>
        ├── bombArea add <from> <to>
        ├── debug <action>
        └── team
            ├── join [<targets>]
            ├── leave [<targets>]
            └── teams
                ├── spectator players <targets> <action>
                └── <teamName>
                    ├── kits <action> [<item>] [<amount>] [dummyAmmoAmount <dummyAmmoAmount>]
                    ├── spawnpoints <action> [<from> <to>]
                    └── players <targets> <action>
```

---

### 1. `/fpsm save`
**描述**：保存所有地图和商店数据。  
**权限**：需 OP 权限（2级）  
**示例**：  
`/fpsm save`  
**说明**：将当前所有地图和商店配置保存至磁盘。

---

### 2. `/fpsm sync`
**描述**：同步所有商店数据。  
**权限**：需 OP 权限（2级）  
**示例**：  
`/fpsm sync`  
**说明**：强制所有商店重新加载并同步其物品数据。

---

### 3. `/fpsm reload`
**描述**：重新加载所有 FPSM 配置。  
**权限**：需 OP 权限（2级）  
**示例**：  
`/fpsm reload`  
**说明**：触发 `FPSMReloadEvent`，重新加载所有地图、商店和模块。

---

### 4. `/fpsm listenerModule add changeItemModule <changedCost> <defaultCost>`
**描述**：创建一个物品替换监听模块。  
**参数**：
- `changedCost`：替换后物品的价格
- `defaultCost`：默认物品的价格  
  **示例**：  
  `/fpsm listenerModule add changeItemModule 150 100`  
  **说明**：主手持“替换物品”，副手持“默认物品”，执行后生成一个监听模块，可用于商店槽位。
---

### 5. `/fpsm shop <gameType> <mapName> modify set <shopName> <shopType> <shopSlot> ...`
**描述**：修改商店槽位配置。  
**参数**：
- `gameType`：游戏类型（如 `fpsm`）
- `mapName`：地图名称
- `shopName`：商店名称
- `shopType`：商店类型（如 `WEAPON`）
- `shopSlot`：槽位编号（1~5）

**子命令**：
- `listenerModule add <listenerModule>`：添加监听模块
- `listenerModule remove <listenerModule>`：移除监听模块
- `groupID <groupID>`：设置组ID
- `cost <cost>`：设置价格
- `item [<item>]`：设置物品（不填则为手持物品）
- `dummyAmmoAmount <dummyAmmoAmount>`：设置虚拟弹药数量（仅对枪械有效）

**示例**：  
`/fpsm shop fpsm dust2 modify set main_shop WEAPON 1 cost 800`  
`/fpsm shop fpsm dust2 modify set main_shop WEAPON 1 item minecraft:diamond_sword`

---

### 6. `/fpsm map create <gameType> <mapName> <from> <to>`
**描述**：创建一个新地图。  
**参数**：
- `gameType`：游戏类型
- `mapName`：地图名称
- `from`：区域起点
- `to`：区域终点  
  **示例**：  
  `/fpsm map create fpsm dust2 ~ ~ ~ ~100 ~50 ~100`  
  **说明**：创建一个名为 `dust2` 的 FPSM 地图，区域为两个坐标点之间的立方体。

---

### 7. `/fpsm map modify <gameType> <mapName> ...`
**描述**：修改地图配置。  
**子命令**：

#### a. `matchEndTeleportPoint <point>`
设置比赛结束后的传送点。  
**示例**：  
`/fpsm map modify fpsm dust2 matchEndTeleportPoint ~ ~ ~`

#### b. `bombArea add <from> <to>`
添加炸弹区域（仅爆破模式有效）。  
**示例**：  
`/fpsm map modify fpsm dust2 bombArea add ~ ~ ~ ~10 ~5 ~10`

#### c. `debug <action>`
调试命令，支持以下动作：
- `start`：开始游戏
- `reset`：重置游戏
- `newRound`：新回合
- `cleanup`：清理地图
- `switch`：切换调试模式

**示例**：  
`/fpsm map modify fpsm dust2 debug start`

#### d. `team join/leave [<targets>]`
加入或离开地图。  
**示例**：  
`/fpsm map modify fpsm dust2 team join`  
`/fpsm map modify fpsm dust2 team leave @a`

#### e. `teams <teamName> kits <action> ...`
管理队伍初始装备：
- `add`：添加物品（手持或指定）
- `clear`：清空装备
- `list`：列出装备
- 可附加 `dummyAmmoAmount` 设置虚拟弹药

**示例**：  
`/fpsm map modify fpsm dust2 teams T kits add`  
`/fpsm map modify fpsm dust2 teams T kits add minecraft:stone 64`  
`/fpsm map modify fpsm dust2 teams T kits add dummyAmmoAmount 90`

#### f. `teams <teamName> spawnpoints <action> [<from> <to>]`
管理重生点：
- `add`：在当前位置添加一个重生点
- `clear`：清空该队伍重生点
- `clearall`：清空所有重生点
- `set`：在区域内批量设置重生点（需指定矩形区域）

**示例**：  
`/fpsm map modify fpsm dust2 teams T spawnpoints add`  
`/fpsm map modify fpsm dust2 teams T spawnpoints set ~-5 ~-5 ~5 ~5`

#### g. `teams <teamName> players <targets> <action>`
管理队伍玩家：
- `join`：加入队伍
- `leave`：离开队伍

**示例**：  
`/fpsm map modify fpsm dust2 teams T players @a join`

---