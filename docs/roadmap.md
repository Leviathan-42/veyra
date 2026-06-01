# Roadmap Ideas

Safer client-side/QoL ideas for Veyra, intended for singleplayer, private servers, debugging, building, or servers that explicitly allow them.

## Overlay / HUD

- armor durability HUD
- tool durability HUD
- potion/effect timers
- FPS/ping/system stats
- coordinate/biome/direction HUD
- clean HUD screenshot mode

## Navigation

- waypoint system
- death marker
- manual breadcrumb markers
- compass overlay pointing to saved waypoints

## Building utilities

- block palette/search helper
- recently used blocks list
- block ID copy UI
- chunk border overlay
- light level overlay
- grid/measurement overlay

## Accessibility

- clearer subtitles
- directional hit indicators
- colorblind-friendly ESP colors
- larger HUD text mode
- high-contrast UI mode

## Launcher improvements

- add Gradle wrapper for mod builds
- add Fabric Loom for standard mod dependency management
- auto-build mod from launcher in dev mode
- better Java detection per OS
- clearer missing-Java errors
- launcher settings page
- profile/instance management

## Branding cleanup

Eventually rename old internal `blocktracker` names if desired:

- Rust crate/package
- Java package
- Maven group
- mod jar base name
- keyring service

This can be done later because changing IDs may affect existing saves/configs/mod folders.
