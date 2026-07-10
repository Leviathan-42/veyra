# Architecture

Veyra has two deployable parts: a desktop launcher and a client-only Fabric mod. They share a managed Minecraft data directory but do not share a runtime process.

## Component map

```text
Svelte UI (launcher/src)
    | Tauri commands and events
Rust backend (launcher/src-tauri/src)
    |-- Microsoft/Xbox/Minecraft authentication
    |-- Java discovery
    |-- Mojang version, asset, library, and native installation
    |-- Fabric and Modrinth profile synchronization
    `-- Java process launch and log streaming

Managed Minecraft instance
    `-- Fabric Loader starts Veyra mod
          |-- keybind and settings state
          |-- incremental loaded-chunk scanner
          |-- local render/HUD overlays
          `-- custom Minecraft screens and FreeCam camera entity
```

## Launcher boundary

`App.svelte` owns presentation and user-selected launch state. It calls typed Tauri commands rather than reading Minecraft data directly. Rust owns filesystem paths, downloads, authentication, launch argument construction, process spawning, and operating-system integration.

The backend emits:

- `launcher-status` for short lifecycle messages
- `launch-log` for bounded process output in the Logs view
- `auth-code` and `auth-error` for the Microsoft webview flow

## Launch data flow

1. The UI loads saved authentication, the Mojang release list, detected Java runtimes, and the selected profile's mods.
2. A launch request includes version, Java path, render profile, fullscreen/window dimensions, optional server, and memory in MB.
3. Rust refreshes authentication, prepares the managed directory, and synchronizes the locally built Veyra jar when available.
4. The selected render profile is populated and mirrored into the active `mods/` directory.
5. Minecraft/Fabric metadata, libraries, assets, and natives are installed or reused.
6. Existing heap flags are removed, then Veyra supplies `-Xms512M` and the selected `-Xmx` value (clamped to 2-16 GB).
7. Java starts with the managed instance as its game directory, and output streams back to the launcher.

Asset installation uses content hashes as stable cache keys. The backend removes duplicate hash entries from Mojang's logical asset index, then runs a maximum of 32 file operations concurrently through one reusable HTTP client. Existing files are validated rather than downloaded again, while interrupted transfers resume from their `.part` files when the CDN supports ranges.

## Mod runtime flow

`BlockTrackerClient` initializes persistent state and client hooks. `MinecraftClientMixin` drives keybinds, incremental scan work, player/death tracking, and render submission from the client tick.

The main responsibilities are separated as follows:

- `BlockTrackerState`: feature flags, target queue, waypoints, HUD layout, and persistence-facing state
- `BlockScan`: budgeted scanning of chunks already present on the client
- `BlockTrackerRenderer`: world-space block, entity, tracer, and waypoint rendering
- `BlockTrackerHud`: screen-space modules and FreeCam status
- `VeyraHudTelemetry`: bounded ping samples, session timing, music state, and local CPS accounting
- `VeyraFreecam`: detached camera state, movement, and renderer-compatible camera entity
- `VeyraBootSequence`: one skippable Minecraft-side startup animation per client session
- `VeyraUi`: shared visual primitives used by all custom Minecraft screens
- screen classes: interaction and layout only
- mixins: narrow integration points into Minecraft rendering, input, options, and screens

## FreeCam rendering model

FreeCam never moves the real player. It installs a client-only marker as Minecraft's camera entity, keeps that marker synchronized with the detached position/rotation, and restores the previous entity and camera mode when disabled. The camera render state disables smart directional occlusion while FreeCam is active so loaded sections remain visible after reversing direction.

This solves renderer/frustum state being anchored to the player's old view and improves compatibility with Sodium-style renderer replacements. It does not ask a remote server for chunks outside the real player's view distance; the client cannot render world data it has never received.

## Persistence boundaries

- Launcher theme and memory selection use webview `localStorage`.
- Account metadata is a small JSON file; refresh tokens use the OS keyring when available.
- Minecraft options remain in the managed game directory.
- Veyra mod settings and waypoints are written by `VeyraSettings` under the Minecraft instance configuration area.

HUD telemetry is intentionally ephemeral. Ping samples, CPS timestamps, the current track, and the session timer are never persisted or transmitted by Veyra.

See [Data layout](data-layout.md) for concrete paths.

## Compatibility principles

- Keep the mod client-only and avoid custom server-required content.
- Keep scan work incremental and bounded per tick.
- Preserve stable internal ids unless a migration is provided.
- Treat renderer integrations as optional compatibility boundaries; vanilla camera/render state remains the source of truth.
- Keep UI state in the UI layer and filesystem/network authority in Rust.
