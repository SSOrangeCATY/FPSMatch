# FPSM Command Help Document (v1.2.3.14)
## Command Tree Structure

```
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

## Command Details

### 1. `/fpsm save`
**Description**: Saves all FPSM data to disk  
**Permission**: Requires OP level 2  
**Example**:  
`/fpsm save`  
**Details**: Writes all current map configurations, shop settings, and team data to persistent storage.

### 2. `/fpsm sync`
**Description**: Synchronizes all shop data  
**Permission**: Requires OP level 2  
**Example**:  
`/fpsm sync`  
**Details**: Forces all shops to reload and update their item data, ensuring consistency across the server.

### 3. `/fpsm reload`
**Description**: Reloads all FPSM configurations  
**Permission**: Requires OP level 2  
**Example**:  
`/fpsm reload`  
**Details**: Triggers a complete reload of all FPSM data, including maps, shops, and modules. This is useful after making manual configuration changes.

### 4. `/fpsm listenerModule add changeItemModule <changedCost> <defaultCost>`
**Description**: Creates a change item listener module  
**Parameters**:
- `changedCost`: Price for the changed item (held in main hand)
- `defaultCost`: Price for the default item (held in offhand)  
  **Example**:  
  `/fpsm listenerModule add changeItemModule 150 100`  
  **Details**: Creates a module that allows changing shop items. The main hand item becomes the changed item, and the offhand item becomes the default item.

### 5. Shop Management Commands

#### `/fpsm shop <gameType> <mapName> modify set <shopName> <shopType> <shopSlot> ...`
**Description**: Modifies shop slot configurations  
**Parameters**:
- `gameType`: Game mode type (e.g., fpsm)
- `mapName`: Name of the map
- `shopName`: Name of the shop
- `shopType`: Type of shop (e.g., WEAPON, EQUIPMENT)
- `shopSlot`: Slot number (1-5)

**Subcommands**:
- `listenerModule add <listenerModule>`: Adds a listener module to the slot
- `listenerModule remove <listenerModule>`: Removes a listener module from the slot
- `groupID <groupID>`: Sets the group ID for the slot
- `cost <cost>`: Sets the price for the item in the slot
- `item [<item>]`: Sets the item for the slot (uses held item if not specified)
- `dummyAmmoAmount <dummyAmmoAmount>`: Sets dummy ammo amount for guns

**Examples**:  
`/fpsm shop fpsm dust2 modify set main_shop WEAPON 1 cost 800`  
`/fpsm shop fpsm dust2 modify set main_shop WEAPON 1 item minecraft:diamond_sword`  
`/fpsm shop fpsm dust2 modify set main_shop WEAPON 1 dummyAmmoAmount 90`

### 6. Map Management Commands

#### `/fpsm map create <gameType> <mapName> <from> <to>`
**Description**: Creates a new map  
**Parameters**:
- `gameType`: Game mode type
- `mapName`: Name for the new map
- `from`: Starting coordinates of the map area
- `to`: Ending coordinates of the map area  
  **Example**:  
  `/fpsm map create fpsm dust2 ~ ~ ~ ~100 ~50 ~100`  
  **Details**: Creates a new map with the specified boundaries. The area is defined by two opposite corners.

#### `/fpsm map modify <gameType> <mapName> ...`
**Description**: Modifies map configurations  
**Subcommands**:

##### a. `matchEndTeleportPoint <point>`
Sets the teleport location after matches end  
**Example**:  
`/fpsm map modify fpsm dust2 matchEndTeleportPoint ~ ~ ~`

##### b. `bombArea add <from> <to>`
Adds a bomb zone (for bomb defusal modes)  
**Example**:  
`/fpsm map modify fpsm dust2 bombArea add ~ ~ ~ ~10 ~5 ~10`

##### c. `debug <action>`
Debug commands for testing:
- `start`: Starts the game
- `reset`: Resets the game
- `newRound`: Starts a new round
- `cleanup`: Cleans up the map
- `switch`: Toggles debug mode

**Example**:  
`/fpsm map modify fpsm dust2 debug start`

##### d. `team join/leave [<targets>]`
Joins or leaves the map  
**Examples**:  
`/fpsm map modify fpsm dust2 team join`  
`/fpsm map modify fpsm dust2 team leave @a`

##### e. `teams <teamName> kits <action> ...`
Manages team starting kits:
- `add`: Adds an item to the kit (uses held item if not specified)
- `clear`: Clears all items from the kit
- `list`: Lists all items in the kit
- Can include `dummyAmmoAmount` for setting ammo on guns

**Examples**:  
`/fpsm map modify fpsm dust2 teams T kits add`  
`/fpsm map modify fpsm dust2 teams T kits add minecraft:stone 64`  
`/fpsm map modify fpsm dust2 teams T kits add dummyAmmoAmount 90`

##### f. `teams <teamName> spawnpoints <action> [<from> <to>]`
Manages team spawn points:
- `add`: Adds a spawn point at current location
- `clear`: Clears all spawn points for the team
- `clearall`: Clears all spawn points for all teams
- `set`: Sets spawn points in a rectangular area (uses current Y position)

**Examples**:  
`/fpsm map modify fpsm dust2 teams T spawnpoints add`  
`/fpsm map modify fpsm dust2 teams T spawnpoints set ~-5 ~-5 ~5 ~5`

##### g. `teams <teamName> players <targets> <action>`
Manages players in teams:
- `join`: Adds players to the team
- `leave`: Removes players from the team

**Example**:  
`/fpsm map modify fpsm dust2 teams T players @a join`

##### h. `teams spectator players <targets> <action>`
Manages spectators:
- `join`: Adds players to spectators
- `leave`: Removes players from spectators

**Example**:  
`/fpsm map modify fpsm dust2 teams spectator players @a[team=] join`