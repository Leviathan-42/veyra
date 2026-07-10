# Fabric client mod

The mod in `mod/` is a Java 25, client-only Fabric utility suite named **Veyra**.

## Metadata

| Field | Value |
| --- | --- |
| Fabric id | `blocktracker` |
| Entrypoint | `dev.blocktracker.BlockTrackerClient` |
| Environment | client |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 (metadata minimum remains 0.19.2) |
| Java | 25 or newer |
| Version | 0.1.0 |

## Default controls

All Veyra key mappings appear in Minecraft Controls and can be rebound.

| Key | Action |
| --- | --- |
| `\` | Open block search; pressing it again closes search |
| `Shift` + `\` | Cancel all active block scans immediately |
| Right Shift | Open or close Veyra Control Center |
| `C` | Toggle FreeCam |
| Mouse wheel in FreeCam | Adjust camera speed from 0.2x to 4.0x |
| `W/A/S/D`, Space, Shift | Move the detached camera |
| Control in FreeCam | Faster movement |

## Custom Minecraft interface

`VeyraUi` supplies the shared dark grid, panels, cards, accents, buttons, logo mark, and theme colors used across:

- the replaced Minecraft title screen;
- the two-column block search/queue screen;
- the tabbed Control Center;
- the startup guide; and
- the HUD workspace/editor.

Appearance settings allow theme and button-style cycling. The title and Control Center use intentionally minimal headers without redundant online/live status badges.

Before the first title screen in each client session, `VeyraBootSequence` renders a short animated startup panel. A click skips it, and the onboarding guide waits until the sequence has completed.

## Block search and target queue

`BlockSearchScreen` searches the block registry by friendly name or identifier and displays suggestions while typing. Enter queues a target. Up to three targets can be active at once; adding a fourth replaces the oldest.

The right side of the search screen shows each queued block, current scan state/progress or match count, and an X that removes the target and cancels its scan immediately. Vanilla ores automatically group regular and deepslate variants when both exist.

## Incremental block scanner

`BlockScan` examines only chunks already known to the client. It never performs a full world scan in one frame.

Current safeguards:

- default radius: 12 chunks, adjustable in Control Center;
- 4,096 block checks per task per tick;
- 96 section checks per task per tick;
- cached section matches;
- cancellation per target;
- delayed retry after an empty result; and
- no forced chunk loading.

This keeps the search responsive while still updating results as loaded chunks change.

## World overlays

`BlockTrackerRenderer` submits local rendering for:

- colored boxes for queued block targets;
- nearest-target tracer lines and labels;
- optional entity boxes filtered by players, animals, and hostile mobs;
- waypoints and a local death marker.

Rendering uses client-known data and Minecraft render helpers. Nothing is added to server registries.

## HUD and workspace

`BlockTrackerHud` provides detachable/configurable modules for:

- tracked blocks and coordinates;
- player coordinates and FPS;
- a bounded 30-second ping graph with current, average, and maximum latency;
- JVM memory use;
- main/offhand and armor durability with low-durability warnings;
- health, hunger, and saturation with warning thresholds;
- active effect timers sorted by expiration;
- the current Minecraft music track;
- real-world clock and world-session timer;
- movement keys and a display-only one-second CPS counter;
- waypoint/death-marker distances; and
- FreeCam status and speed.

The HUD editor supports dragging modules, detaching groups, scaling, compact/expanded layouts, and per-module visibility. PvP, Building, and Screenshot presets configure sensible module groups; changing the layout returns the preset label to Custom. Screenshot hides Veyra HUD elements and the custom crosshair without changing Minecraft's network behavior.

`VeyraHudTelemetry` samples ping twice per second into a fixed 60-value ring, prunes click timestamps after one second, and resets session data when entering a world. It only observes information already available to the local client. `VeyraSettings` persists user-facing layout settings without writing on every drag event.

## Waypoints

Control Center can add a marker at the player, mark the looked-at block, remove the nearest marker, clear all markers, or clear the death marker. Veyra stores up to 24 waypoints and filters them by dimension.

## FreeCam

`VeyraFreecam` separates the camera from the real player. Player movement input is cleared while active, and the original camera type/entity is restored on exit.

The implementation uses a client-only `Marker` as Minecraft's active camera entity. This is important for vanilla, Sodium, and culling integrations that consult the camera entity rather than only the final camera matrix. `FreecamCameraMixin` also disables smart directional occlusion in the extracted camera render state while active, forcing visibility to be rebuilt around the detached camera and keeping already-loaded sections visible when looking behind the original player.

FreeCam does not move or spoof the networked player. On remote servers, it cannot display chunks the server has not sent around the real player.

## Other visual features

- **Fullbright:** local lightmap render-state adjustment.
- **Custom crosshair:** configurable style/size with red, cyan, and green reach feedback.
- **Title theming:** fully custom Veyra title layout while retaining singleplayer, multiplayer, options, and quit behavior.

## State and persistence

`BlockTrackerState` owns target queues, scan results, feature toggles, waypoint/death state, HUD modules, crosshair settings, and scan radius. `VeyraSettings` is the persistence adapter. Renderer and screen classes consume state but do not own filesystem policy.

## Mixins

| Mixin | Purpose |
| --- | --- |
| `MinecraftClientMixin` | Client tick/update integration |
| `OptionsMixin` | Veyra key mapping registration |
| `HudMixin` | HUD render-state extraction |
| `CrosshairMixin` | Replace the vanilla crosshair when enabled |
| `LightmapRenderStateExtractorMixin` | Local fullbright adjustment |
| `FreecamCameraMixin` | Detached camera position/rotation and 360-degree occlusion behavior |
| `FreecamInputMixin` | Suppress real player movement input |
| `FreecamMouseMixin` | Camera look and wheel-speed controls |
| `ScreenThemeMixin` | Shared custom screen background behavior |
| `TitleScreenThemeMixin` | Custom title screen buttons and rendering |
| `AbstractButtonThemeMixin` | Veyra button rendering |

`CameraAccessor` exposes only the protected camera setters required by FreeCam.

## Client-only boundary

The mod does not require installation on the server and does not define custom blocks, items, entities, packets, or gameplay automation. Server permission is still a policy question; see [Server safety](server-safety.md).
