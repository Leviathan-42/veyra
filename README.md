# Veyra

Veyra is a polished desktop launcher and client-side Fabric utility suite for Minecraft Java Edition. The repository contains a Tauri/Svelte launcher, a Rust installation and authentication backend, and the Veyra client mod.

## Highlights

- Microsoft account sign-in and saved sessions
- automatic Minecraft, Fabric, library, asset, and native installation
- isolated VulkanMod and OpenGL/Iris mod profiles
- automatic primary-monitor resolution or a custom window size
- persistent 2-16 GB Java memory allocation
- dark/light launcher themes and a reduced-motion-aware startup animation
- block search with three queued targets, incremental scanning, ESP, and tracers
- entity overlays, waypoints, death markers, customizable HUD modules, fullbright, and crosshairs
- ping history, status warnings, effect countdowns, music, clocks, keystrokes, display-only CPS, and HUD presets
- detached FreeCam with renderer-aware 360-degree visibility for already-loaded chunks
- custom Minecraft boot, title, search, settings, tutorial, and HUD editor screens

## Repository layout

| Path | Purpose |
| --- | --- |
| `launcher/` | Tauri 2 desktop app, Svelte 5 UI, and Rust backend |
| `mod/` | Java 25 client-side Fabric mod |
| `docs/` | Architecture, development, behavior, storage, and troubleshooting docs |

## Quick start

Requirements:

- Node.js and npm
- Rust stable with Cargo
- Java 25
- Gradle 9.x for the mod build

```powershell
cd launcher
npm install
npm run tauri dev
```

Launch Minecraft once from Veyra so the target game jar and libraries exist, then build the mod:

```powershell
cd ..\mod
gradle build
```

The launcher automatically copies `mod/build/libs/block-tracker-0.1.0.jar` into its managed Minecraft instance on the next launch.

## Documentation

Start with [the documentation index](docs/README.md). Important references include:

- [Architecture](docs/architecture.md)
- [Launcher](docs/launcher.md)
- [Fabric mod](docs/mod.md)
- [Build and development](docs/build.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Server safety and rules](docs/server-safety.md)

## Project identity

The user-facing product name is **Veyra**. The stable internal identifiers still use the original Block Tracker name (`blocktracker`, `dev.blocktracker`, and `block-tracker-*`) to avoid breaking stored settings and installed instances.
