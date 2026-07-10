# Data layout

Veyra owns a dedicated Minecraft instance under the operating system's user data directory.

## Application root

| OS | Typical path |
| --- | --- |
| Windows | `%APPDATA%/VeyraLauncher` |
| macOS | `~/Library/Application Support/VeyraLauncher` |
| Linux | `$XDG_DATA_HOME/VeyraLauncher` or `~/.local/share/VeyraLauncher` |

If `BlockTrackerLauncher` exists and `VeyraLauncher` does not, the backend attempts a one-time directory rename. If that rename fails, the legacy path remains usable.

## Layout

```text
VeyraLauncher/
|-- account.json
`-- minecraft/
    |-- assets/
    |-- config/
    |   `-- veyra-client.properties
    |-- libraries/
    |-- logs/
    |   `-- latest.log
    |-- mods/
    |-- natives/
    |-- profiles/
    |   |-- opengl/mods/
    |   `-- vulkan/mods/
    `-- versions/
        `-- <version>/
```

Exact native/library subdirectories follow Mojang/Fabric metadata and may vary by version.

## Active and profile mods

`minecraft/profiles/<profile>/mods` is the durable source for each render profile. At launch, the selected profile is mirrored into `minecraft/mods`, which is the active folder read by Fabric.

Put manual additions in the profile folder opened by the launcher. Files placed only in the active folder may be replaced by the next profile synchronization.

## Veyra mod settings

The mod writes:

```text
minecraft/config/veyra-client.properties
```

It contains feature toggles, scan radius, HUD layout/module state and preset, crosshair settings, waypoints, theme/button appearance, and related client preferences. Saves are debounced and written through a temporary file with an atomic move when supported. Live ping history, CPS timestamps, music, and session time are not persisted.

## Authentication

Public account/profile metadata is stored in:

```text
VeyraLauncher/account.json
```

Refresh tokens use the OS keyring where supported. The keyring service/user identity is:

```text
dev.blocktracker.launcher / minecraft_refresh_token
```

Do not commit account files, tokens, instance logs, or managed game data.

## Launcher webview settings

The frontend currently stores these local values in the Tauri webview profile:

- `veyra-theme`
- `veyra-memory-gb`

They are separate from Minecraft's `options.txt` and Veyra's mod properties file.

## Logs

Minecraft/Fabric output is written to `minecraft/logs/latest.log` and streamed into the launcher Logs view. The UI retains a bounded recent window rather than an unbounded in-memory log.
