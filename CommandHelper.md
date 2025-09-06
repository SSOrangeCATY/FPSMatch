# FPSM 命令帮助(v1.2.4)

## 基础命令
- `/fpsm help` - 显示此帮助信息
- `/fpsm save` - 保存所有数据
- `/fpsm sync` - 同步商店数据
- `/fpsm reload` - 重新加载配置

## 监听模块命令
- `/fpsm listener_module add change_item_module <changed_cost> <default_cost>` - 添加物品变更监听模块

## 商店配置命令
- `/fpsm shop <gameType> <mapName> modify set <shopName> <shopType> <shopSlot>` - 商店配置
  - `listener_module add/remove <module>` - 添加/移除监听模块
  - `group_id <id>` - 设置组ID
  - `cost <amount>` - 设置价格
  - `item` - 设置物品(手持物品)
  - `dummy_ammo_amount <amount>` - 设置虚拟弹药数量

## 地图命令
### 创建地图
- `/fpsm map create <gameType> <mapName> <from> <to>` - 创建地图

### 地图配置
- `/fpsm map modify <gameType> <mapName>` - 地图配置
  - `match_end_teleport_point <point>` - 设置比赛结束传送点
  - `bomb_area add <from> <to>` - 添加炸弹区域
  - `debug <start|reset|newRound|cleanup|switch>` - 调试命令

### 队伍管理
- `team join/leave [targets]` - 加入/离开队伍
- `team teams spectator players <targets> <action>` - 观察者操作
- `team teams <teamName> kits <action>` - 队伍装备配置
- `team teams <teamName> spawnpoints <action>` - 重生点配置
- `team teams <teamName> players <targets> <action>` - 队伍玩家操作

## 使用提示
使用 **TAB键** 自动补全命令参数

---

### 参数说明：
- `<gameType>` - 游戏类型
- `<mapName>` - 地图名称
- `<shopName>` - 商店名称
- `<shopType>` - 商店类型
- `<shopSlot>` - 商店槽位(1-5)
- `<changed_cost>` - 变更后价格
- `<default_cost>` - 默认价格
- `<module>` - 监听模块名称
- `<id>` - 组ID数字
- `<amount>` - 数量/价格
- `<point>` - 坐标位置
- `<from>/<to>` - 起始/结束坐标
- `<targets>` - 目标玩家
- `<action>` - 操作动作
- `<teamName>` - 队伍名称