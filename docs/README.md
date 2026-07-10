# Veyra documentation

These documents describe the current launcher and mod, not an aspirational design.

## Start here

- [Architecture](architecture.md) - component boundaries and launch/runtime data flow
- [Launcher](launcher.md) - UI, commands, profiles, authentication, memory, and display settings
- [Fabric mod](mod.md) - features, controls, state, scanning, rendering, and mixins
- [Build and development](build.md) - prerequisites, build commands, verification, and installation
- [Data layout](data-layout.md) - managed instance, profiles, auth, logs, and settings locations
- [Troubleshooting](troubleshooting.md) - common launcher, Java, mod, rendering, and FreeCam issues
- [Server safety](server-safety.md) - client-side boundary and server-rule caveats
- [Roadmap](roadmap.md) - focused remaining engineering work

## Current targets

| Component | Target |
| --- | --- |
| Launcher UI | Svelte 5 + Vite 7 |
| Desktop shell | Tauri 2 |
| Backend | Rust |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 |
| Java | 25 |
| Veyra version | 0.1.0 |

## Naming

The public name is **Veyra**. These older identifiers are intentionally retained for compatibility:

- Fabric mod id: `blocktracker`
- Java package: `dev.blocktracker`
- launcher package/crate: `block-tracker-launcher`
- mod jar base name: `block-tracker`
- keyring service: `dev.blocktracker.launcher`

Treat changes to those identifiers as migrations, not cosmetic renames.
