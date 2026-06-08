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

Veyra keybinds are registered in Minecraft's Controls menu and can be rebound:

- `\` by default — open block search screen; pressing it again while search is open cancels/closes search
- `Right Shift` by default — open/close Veyra config menu
- `C` by default — toggle freecam

Waypoint creation is currently available from the Veyra config menu.

## Main features

### Block search

File: `BlockSearchScreen.java`

Lets the user search for a block by name/id, suggests matching registry IDs, and adds a target block type. Veyra tracks up to three block targets at once, replacing the oldest target when a fourth is added. Vanilla ores automatically track both regular and deepslate variants when both exist.

### Block scan

File: `BlockScan.java`

Scans loaded client chunks near the player for the closest matching block. Default block-tracker scan radius is `12` chunks from the player, limited to chunks already loaded/known by the client. It reads client-known world data only.

### Block ESP/tracer

File: `BlockTrackerRenderer.java`

Draws:

- colored boxes over up to three target blocks
- billboard/HUD labels
- tracer lines from player body to target blocks

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

Optional Veyra menu toggle that adjusts the local lightmap render state. It does not send packets.

### Freecam

Files: `VeyraFreecam.java`, `FreecamCameraMixin.java`, `FreecamInputMixin.java`, `FreecamMouseMixin.java`

Configurable toggle key, camera-only movement, player movement cancellation while active, and scroll-wheel speed adjustment with a small HUD speed slider.

### Custom crosshair

Files: `BlockTrackerHud.java`, `CrosshairMixin.java`

Optional custom crosshair. It is red when nothing is in reach, cyan when a block is in reach, and green when an entity is in reach.

### Config menu

File: `BlockTrackerConfigScreen.java`

Veyra menu with toggles for:

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
- Clear Targets

## Vanilla server compatibility

The mod remains client-side. These overlays read client-known data and render local HUD/gizmos. They do not require the server to install Veyra and should remain technically compatible with vanilla servers as long as future features do not add server-required packets/content or gameplay/network modifications.

## Mixins

File: `blocktracker.mixins.json`

Current client mixins:

- `MinecraftClientMixin` — keybind ticking and renderer emit call
- `OptionsMixin` — registers Veyra keybinds in Minecraft Controls
- `HudMixin` — HUD overlay extraction
- `CrosshairMixin` — hides vanilla crosshair while Veyra custom crosshair is enabled
- `LightmapRenderStateExtractorMixin` — optional local fullbright lightmap adjustment
- `FreecamCameraMixin`, `FreecamInputMixin`, `FreecamMouseMixin` — freecam camera, input cancellation, mouse look, and scroll speed control
- `ScreenThemeMixin` — Veyra screen theming
- `TitleScreenThemeMixin` — fully custom Veyra title screen/menu
- `AbstractButtonThemeMixin` — button theme changes
