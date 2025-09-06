# FPSM Command Help(v1.2.4)

## Basic Commands
- `/fpsm help` - Show this help message
- `/fpsm save` - Save all data
- `/fpsm sync` - Sync shop data
- `/fpsm reload` - Reload configuration

## Listener Module Commands
- `/fpsm listener_module add change_item_module <changed_cost> <default_cost>` - Add item change listener module

## Shop Configuration Commands
- `/fpsm shop <gameType> <mapName> modify set <shopName> <shopType> <shopSlot>` - Shop configuration
  - `listener_module add/remove <module>` - Add/remove listener module
  - `group_id <id>` - Set group ID
  - `cost <amount>` - Set price
  - `item` - Set item (held item)
  - `dummy_ammo_amount <amount>` - Set dummy ammo amount

## Map Commands
### Create Map
- `/fpsm map create <gameType> <mapName> <from> <to>` - Create map

### Map Configuration
- `/fpsm map modify <gameType> <mapName>` - Map configuration
  - `match_end_teleport_point <point>` - Set match end teleport point
  - `bomb_area add <from> <to>` - Add bomb area
  - `debug <start|reset|newRound|cleanup|switch>` - Debug commands

### Team Management
- `team join/leave [targets]` - Join/leave team
- `team teams spectator players <targets> <action>` - Spectator operations
- `team teams <teamName> kits <action>` - Team kit configuration
- `team teams <teamName> spawnpoints <action>` - Spawnpoint configuration
- `team teams <teamName> players <targets> <action>` - Team player operations

## Usage Tips
Use **TAB key** for auto-completion of command parameters

---

### Parameter Description:
- `<gameType>` - Game type
- `<mapName>` - Map name
- `<shopName>` - Shop name
- `<shopType>` - Shop type
- `<shopSlot>` - Shop slot (1-5)
- `<changed_cost>` - Changed cost
- `<default_cost>` - Default cost
- `<module>` - Listener module name
- `<id>` - Group ID number
- `<amount>` - Quantity/Price
- `<point>` - Coordinate position
- `<from>/<to>` - Start/End coordinates
- `<targets>` - Target players
- `<action>` - Operation action
- `<teamName>` - Team name