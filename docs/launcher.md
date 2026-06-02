# Launcher

Path: `launcher/`

The launcher is a desktop app built with:

- Tauri 2
- Rust backend
- Svelte 5 frontend
- Vite

## Frontend

Main file: `launcher/src/App.svelte`

The UI handles:

- Microsoft sign-in button/state
- Minecraft version selection
- Java path input
- launch button
- mods folder/library panel
- popular server panel with copy-IP and quick-play launch buttons
- process logs streamed from the Rust backend

Important invoke commands used by the frontend:

- `auth_open_login_window`
- `auth_complete_login`
- `auth_load_saved`
- `auth_sign_out`
- `list_versions`
- `install_and_launch` — accepts optional `quickPlayServer` for direct server launch
- `mods_dir`
- `list_installed_mods`
- `open_mods_folder`

## Rust backend

Main files:

- `launcher/src-tauri/src/lib.rs` — Tauri command registration/window auth flow
- `launcher/src-tauri/src/auth.rs` — Microsoft/Xbox/Minecraft auth
- `launcher/src-tauri/src/minecraft.rs` — version install, Fabric install, launch args
- `launcher/src-tauri/src/paths.rs` — cross-platform data directory resolution
- `launcher/src-tauri/src/types.rs` — shared serialized types

## Auth flow

The launcher opens a Microsoft login webview. On redirect to:

```text
https://login.live.com/oauth20_desktop.srf
```

it extracts the authorization code, exchanges it for Microsoft tokens, then performs Xbox Live/XSTS/Minecraft login.

Refresh tokens are stored through the OS keyring when possible, with account metadata stored in the launcher data dir.

## Minecraft launch flow

`install_and_launch` does roughly:

1. Load/refresh account.
2. Create the launcher game directory.
3. Sync local development Veyra mod jar if built.
4. Download compatible Fabric/VulkanMod client mods from Modrinth when available.
5. Download selected Minecraft version metadata/client jar.
5. Download libraries/assets/natives.
6. Install Fabric loader/runtime libraries.
7. Build JVM and game args.
9. Optionally append a `--quickPlayMultiplayer` target when launched from a server card.
10. Spawn Java and stream stdout/stderr back to the UI.

## Development mod sync

If this exists:

```text
mod/build/libs/block-tracker-0.1.0.jar
```

it is copied into the launcher's managed `mods/` folder before launch. Old `block-tracker-*.jar` files are removed first.

## Managed client mods

On launch, Veyra tries to install compatible Fabric builds for the selected Minecraft version:

- Fabric API
- VulkanMod
- Lithium
- FerriteCore
- EntityCulling
- ModernFix
- Cloth Config API
- Noisium
- Krypton

Veyra intentionally does not install Sodium, Iris Shaders, ImmediatelyFast, More Culling, Enhanced Block Entities, or Dynamic FPS because those are listed as incompatible or visually broken with VulkanMod.

If Modrinth has no compatible build for a selected version, the launcher logs a skip and continues launching.
