# FPSM Command Help

## 1. /fpsm loadOld
**Description:** Load old map data.  
**Parameters:** None  
**Example:** `/fpsm loadOld`  
**Explanation:** This command loads old map data from the archive and registers it in the game.

## 2. /fpsm save
**Description:** Save current game data.  
**Parameters:** None  
**Example:** `/fpsm save`  
**Explanation:** This command saves the current game data to the archive.

## 3. /fpsm sync
**Description:** Synchronize shop data.  
**Parameters:** None  
**Example:** `/fpsm sync`  
**Explanation:** This command synchronizes the shop data of all maps to ensure data consistency.

## 4. /fpsm reload
**Description:** Reload game data.  
**Parameters:** None  
**Example:** `/fpsm reload`  
**Explanation:** This command reloads the game data, suitable for reloading after data updates.

## 5. /fpsm listenerModule
**Description:** Manage listener modules.  
**Parameters:**
- `add changeItemModule <changedCost> <defaultCost>`: Add a listener module to change item cost.
    - `<changedCost>`: The changed item cost.
    - `<defaultCost>`: The default item cost.  
      **Example:** `/fpsm listenerModule add changeItemModule 10 5`  
      **Explanation:** This command adds a listener to change item cost. The main hand item will be set to the changed item, and the off-hand item will be set to the default item.

## 6. /fpsm shop
**Description:** Manage shops.  
**Parameters:**
- `<gameType>`: Game type.
- `<mapName>`: Map name.
- `<shopName>`: Shop name.
- `<shopType>`: Shop type.
- `<shopSlot>`: Shop slot.  
  **Subcommands:**
- `modify set <shopName> <shopType> <shopSlot> listenerModule add <listenerModule>`: Add a listener module to the shop.
    - `<listenerModule>`: Listener module name.  
      **Example:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 listenerModule add module1`  
      **Explanation:** This command adds a listener module to the specified shop slot.
- `modify set <shopName> <shopType> <shopSlot> listenerModule remove <listenerModule>`: Remove a listener module from the shop.
    - `<listenerModule>`: Listener module name.  
      **Example:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 listenerModule remove module1`  
      **Explanation:** This command removes a listener module from the specified shop slot.
- `modify set <shopName> <shopType> <shopSlot> groupID <groupID>`: Modify the group ID of the shop slot.
    - `<groupID>`: Group ID.  
      **Example:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 groupID 2`  
      **Explanation:** This command modifies the group ID of the specified shop slot.
- `modify set <shopName> <shopType> <shopSlot> cost <cost>`: Modify the cost of the shop slot.
    - `<cost>`: Cost.  
      **Example:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 cost 10`  
      **Explanation:** This command modifies the cost of the specified shop slot.
- `modify set <shopName> <shopType> <shopSlot> item <item>`: Modify the item of the shop slot.
    - `<item>`: Item.  
      **Example:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 item diamond_sword`  
      **Explanation:** This command modifies the item of the specified shop slot.
- `modify set <shopName> <shopType> <shopSlot> dummyAmmoAmount <dummyAmmoAmount>`: Modify the dummy ammo amount of the shop slot.
    - `<dummyAmmoAmount>`: Dummy ammo amount.  
      **Example:** `/fpsm shop fpsm map1 modify set shop1 weapon 1 dummyAmmoAmount 50`  
      **Explanation:** This command modifies the dummy ammo amount of the specified shop slot.

## 7. /fpsm map
**Description:** Manage maps.  
**Parameters:**
- `<gameType>`: Game type.
- `<mapName>`: Map name.
- `<from>`: Starting coordinates of the area.
- `<to>`: Ending coordinates of the area.
- `<point>`: Teleport point coordinates.
- `<teamName>`: Team name.
- `<action>`: Debug action, such as start, reset, newRound, cleanup, switch.  
  **Subcommands:**
- `create <gameType> <mapName> from <from> to <to>`: Create a new map.  
  **Example:** `/fpsm map create fpsm map1 from 0 0 0 to 100 100 100`  
  **Explanation:** This command creates a new map, with the area defined by the two diagonal points `<from>` and `<to>`.
- `modify <mapName> matchEndTeleportPoint <point>`: Modify the match end teleport point of the map.  
  **Example:** `/fpsm map modify map1 matchEndTeleportPoint 50 50 50`  
  **Explanation:** This command modifies the match end teleport point of the specified map.
- `modify <mapName> bombArea add from <from> to <to>`: Add a bomb area to the map.  
  **Example:** `/fpsm map modify map1 bombArea add from 0 0 0 to 10 10 10`  
  **Explanation:** This command adds a bomb area to the specified map, with the area defined by the two diagonal points `<from>` and `<to>`.
- `modify <mapName> debug <action>`: Perform debug actions on the map.  
  **Example:** `/fpsm map modify map1 debug start`  
  **Explanation:** This command performs debug actions on the specified map, with the specific action determined by the `<action>` parameter.
- `modify <mapName> join`: Join the map.  
  **Example:** `/fpsm map modify map1 join`  
  **Explanation:** This command joins the player to the specified map.
- `modify <mapName> team <teamName> kits <action> <item>`: Modify the starting equipment of the team.
    - `<action>`: Action type, such as add, clear, list.
    - `<item>`: Item.  
      **Example:** `/fpsm map modify map1 team team1 kits add diamond_sword`  
      **Explanation:** This command adds starting equipment to the specified team.