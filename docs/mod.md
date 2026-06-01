# Fabric Mod

Path: `mod/`

The mod is a client-side Fabric utility overlay named **Veyra**.

## Metadata

File: `mod/src/main/resources/fabric.mod.json`

- mod id: `blocktracker`
- name: `Veyra`
- environment: `client`
- entrypoint: `dev.blocktracker.BlockTrackerClient`
- Fabric loader: `>=0.19.2`
- Java: `>=25`

## Keybinds

Currently hardcoded in `MinecraftClientMixin`:

- `\` — open block search screen; pressing it again while search is open cancels/closes search
- `Right Shift` — open/close Veyra config menu

Waypoint creation is currently available from the Veyra config menu.

## Main features

### Block search

File: `BlockSearchScreen.java`

Lets the user search for a block by name/id, suggests matching registry IDs, and selects a target block type. Vanilla ores automatically track both regular and deepslate variants when both exist.

### Block scan

File: `BlockScan.java`

Scans loaded client chunks near the player for the closest matching block. Default block-tracker scan radius is `12` chunks from the player, limited to chunks already loaded/known by the client. It reads client-known world data only.

### Block ESP/tracer

File: `BlockTrackerRenderer.java`

Draws:

- box over target block
- billboard label
- tracer line from player body to target block

Uses Minecraft gizmos render helpers.

### Entity ESP

File: `BlockTrackerRenderer.java`

Optional client-side boxes around known nearby entities, filtered by:

- players
- animals/passive categories
- hostile mobs

### HUD overlays

File: `BlockTrackerHud.java`

Shows:

- current tracked block and coordinates
- player coordinates
- FPS
- ping
- JVM RAM usage
- main hand/offhand durability
- armor durability
- active potion/effect timers
- waypoint distances
- death marker distance

### Waypoints / death marker

Files: `BlockTrackerState.java`, `BlockTrackerHud.java`, `BlockTrackerRenderer.java`

The config menu can add a waypoint at the player's current position and clear waypoints. Death marker is captured locally when the player dies. Waypoints/death marker are local and client-side.

### Fullbright

File: `LightmapRenderStateExtractorMixin.java`

Optional Right Shift menu toggle that adjusts the local lightmap render state. It does not send packets.

### Config menu

File: `BlockTrackerConfigScreen.java`

Right Shift menu with toggles for:

- Block ESP
- Tracer
- Top HUD
- Entity Hitboxes
- Players
- Animals
- Hostile Mobs
- Stats HUD
- Waypoints HUD
- Fullbright
- Add Waypoint
- Clear Waypoints
- Clear Target

## Vanilla server compatibility

The mod remains client-side. These overlays read client-known data and render local HUD/gizmos. They do not require the server to install Veyra and should remain technically compatible with vanilla servers as long as future features do not add server-required packets/content or gameplay/network modifications.

## Mixins

File: `blocktracker.mixins.json`

Current client mixins:

- `MinecraftClientMixin` — key checks and renderer emit call
- `HudMixin` — HUD overlay extraction
- `LightmapRenderStateExtractorMixin` — optional local fullbright lightmap adjustment
- `ScreenThemeMixin` — Veyra screen theming
- `TitleScreenThemeMixin` — fully custom Veyra title screen/menu
- `AbstractButtonThemeMixin` — button theme changes
