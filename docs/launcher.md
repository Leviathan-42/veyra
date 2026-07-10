# Launcher

The launcher in `launcher/` is a Tauri 2 desktop application with a Svelte 5/Vite frontend and a Rust backend.

## User interface

`launcher/src/App.svelte` provides four views:

- **Play** - account/session summary, selected Minecraft build, and launch action
- **Mod library** - installed jars for the selected renderer profile and a folder shortcut
- **Settings** - Minecraft version, renderer, Java, RAM, display, fullscreen, and theme controls
- **Launch log** - bounded live output from installation and the Minecraft process

The launcher defaults to dark mode and supports a persistent light theme. Its animated startup sequence is skipped when the operating system requests reduced motion.

## Settings

### Minecraft and Java

The version picker is populated from Mojang's version manifest and defaults to the newest stable release. Java runtimes are discovered locally and marked compatible when they meet the current Java 25 requirement; a manual executable path is also accepted.

### Memory

Memory allocation is selectable from 2 through 16 GB, with 4/6/8/12 GB presets. The selected value is saved as `veyra-memory-gb` in launcher webview local storage and passed to Rust as MB.

The backend removes existing heap flags and adds:

```text
-Xms512M
-Xmx<selected MB>M
```

### Display

Auto mode reads the primary monitor size using Tauri's window API. Custom mode accepts explicit width and height. Fullscreen is independent of the resolution mode.

### Render profiles

Profiles isolate renderer-specific mods under:

```text
<game dir>/profiles/<profile>/mods
```

The selected folder is mirrored into the active `minecraft/mods` directory at launch.

**Vulkan profile**

- VulkanMod

**OpenGL + Iris profile**

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

Unavailable Modrinth builds are logged and skipped rather than aborting the entire launch.

## Tauri command surface

Commands are registered in `launcher/src-tauri/src/lib.rs`:

| Command | Responsibility |
| --- | --- |
| `auth_start_login` | Create an OAuth start payload |
| `auth_open_login_window` | Open/focus the Microsoft authentication webview |
| `auth_complete_login` | Exchange the returned code through Xbox/XSTS/Minecraft auth |
| `auth_load_saved` | Restore and refresh a saved account |
| `auth_sign_out` | Clear saved account/token state |
| `list_versions` | Return Mojang version choices |
| `detect_java_runtimes` | Discover and classify Java installations |
| `install_and_launch` | Prepare the instance and start Minecraft |
| `mods_dir` / `profile_mods_dir` | Return managed mod paths |
| `list_installed_mods` / `list_profile_installed_mods` | List jar metadata |
| `open_mods_folder` / `open_profile_mods_folder` | Open folders with the native file manager |

`install_and_launch` accepts Java path, Minecraft version, optional quick-play server, window dimensions, fullscreen, render profile, and optional memory MB.

## Authentication

The launcher opens Microsoft login in a dedicated webview. Navigation to Microsoft's desktop OAuth redirect is intercepted, and the authorization code is returned to the main window through an `auth-code` event. Rust then completes Microsoft, Xbox Live, XSTS, and Minecraft authentication.

Account metadata is stored in `account.json`. Refresh tokens use the operating-system keyring where supported.

## Installation and launch pipeline

The backend in `minecraft.rs`:

1. loads or refreshes the account;
2. creates the managed game directory;
3. syncs the latest local Veyra development jar if present;
4. resolves the render profile and compatible managed mods;
5. downloads version metadata, client jar, libraries, assets, and natives as needed;
6. installs Fabric Loader libraries;
7. expands Mojang JVM/game argument rules;
8. applies memory, resolution, fullscreen, and optional quick-play arguments;
9. starts Java in the managed directory; and
10. streams stdout/stderr to the UI.

Minecraft asset objects are deduplicated by content hash and downloaded or cache-verified with a shared HTTP connection pool at a bounded concurrency of 32. Individual downloads retain SHA-1/size validation, resumable `.part` files, and retry/backoff behavior. Progress updates are aggregated so thousands of tiny assets do not overwhelm the frontend event loop.

## Frontend state ownership

Persistent browser-side settings currently include:

- `veyra-theme`
- `veyra-memory-gb`

Version, Java, display, fullscreen, and render-profile selections currently live for the launcher session unless otherwise stored by the underlying Minecraft options/profile data.

## Important source files

- `launcher/src/App.svelte` - state, command calls, and markup
- `launcher/src/styles.css` - visual system, themes, responsive layout, and startup animation
- `launcher/src-tauri/src/lib.rs` - command registration and auth window
- `launcher/src-tauri/src/auth.rs` - account/token flow
- `launcher/src-tauri/src/minecraft.rs` - install/profile/launch pipeline
- `launcher/src-tauri/src/paths.rs` - cross-platform storage paths
- `launcher/src-tauri/src/types.rs` - serialized command/event types
