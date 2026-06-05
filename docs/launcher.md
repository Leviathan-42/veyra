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
- Java runtime auto-detection + manual Java path input
- OpenGL/VulkanMod render profile selection
- launch button
- per-profile mods folder/library panel
- popular server panel with copy-IP and quick-play launch buttons
- process logs streamed from the Rust backend

Important invoke commands used by the frontend:

- `auth_open_login_window`
- `auth_complete_login`
- `auth_load_saved`
- `auth_sign_out`
- `list_versions`
- `detect_java_runtimes`
- `install_and_launch` — accepts optional `quickPlayServer` and render profile for direct server launch
- `mods_dir`
- `profile_mods_dir`
- `list_installed_mods`
- `list_profile_installed_mods`
- `open_mods_folder`
- `open_profile_mods_folder`

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
4. Detect/use the newest compatible Java runtime when no manual Java path is provided.
5. Download compatible profile mods from Modrinth when available:
   - Vulkan profile: lean VulkanMod stack.
   - OpenGL profile: Iris/Sodium/performance stack.
6. Mirror the selected profile's mods folder into the active Minecraft `mods/` folder.
7. Download selected Minecraft version metadata/client jar.
8. Download libraries/assets/natives.
9. Install Fabric loader/runtime libraries.
10. Build JVM and game args.
11. Optionally append a `--quickPlayMultiplayer` target when launched from a server card.
12. Spawn Java and stream stdout/stderr back to the UI.

## Development mod sync

If this exists:

```text
mod/build/libs/block-tracker-0.1.0.jar
```

it is copied into the launcher's managed `mods/` folder before launch. Old `block-tracker-*.jar` files are removed first.

## Render profiles and managed client mods

Veyra has separate profile mod folders under:

```text
<VeyraLauncher>/minecraft/profiles/<profile>/mods
```

On launch, the selected profile folder is mirrored into Minecraft's active `mods/` folder.

### VulkanMod profile

Lean profile intended for VulkanMod compatibility:

- VulkanMod

### OpenGL profile

OpenGL/Iris profile intended for standard shader and performance mod use:

- Fabric API
- Sodium
- Iris Shaders
- Lithium
- FerriteCore
- EntityCulling
- ModernFix
- Cloth Config API
- Noisium
- Krypton
- ImmediatelyFast
- More Culling
- Enhanced Block Entities
- Dynamic FPS

The launcher can open the selected profile's mods folder so users can add their own profile-specific mods.

If Modrinth has no compatible build for a selected version, the launcher logs a skip and continues launching.
