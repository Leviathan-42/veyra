# Troubleshooting

## Launcher does not start

Run the frontend and Rust checks independently:

```powershell
cd launcher
npm.cmd run build
cargo check --manifest-path src-tauri\Cargo.toml
```

On Windows, use `npm.cmd` if PowerShell blocks `npm.ps1` through its execution policy.

## Java is missing or incompatible

Minecraft 26.2 and this mod require Java 25. Open Settings and select a compatible detected runtime, or provide the path to a Java 25 executable. The launcher reports detected vendor/version information.

On the packaged Windows launcher, Java 25 is provisioned automatically from Eclipse Temurin when no compatible runtime is installed.

## Asset installation is slow

The first Minecraft install must download Mojang's asset set and can still depend on CDN, disk, antivirus, and network speed. Veyra downloads or verifies up to 32 unique assets concurrently and reuses valid cached objects on later launches.

Do not delete `minecraft/assets` between launches. Interrupted downloads leave `.part` files that Veyra attempts to resume. The launcher status shows batched completion progress, and the launch log records the total verification time.

## The mod is not present in Minecraft

1. Build `mod/build/libs/block-tracker-0.1.0.jar`.
2. Launch Minecraft through Veyra again; development sync happens during launch.
3. Confirm the active file exists at `<VeyraLauncher>/minecraft/mods/block-tracker-0.1.0.jar`.
4. Check `logs/latest.log` for Fabric or mixin errors.

The build and installed jar should have the same SHA-256 hash.

## Block search pauses or feels slow

The scanner only checks client-loaded chunks and processes a fixed amount of work per tick. Lower the scan range in Veyra Control Center if a large loaded world still causes pressure. Removing a queued target with its X immediately cancels its scan task.

Current scan budgets are 4,096 block checks and 96 section checks per target task per tick, with a delayed retry for empty results.

## FreeCam shows empty terrain

There are two different cases:

- **Loaded terrain disappears when turning behind the player:** the detached camera entity and FreeCam occlusion override are intended to prevent this. Make sure the latest Veyra jar is installed and restart Minecraft after updating it.
- **Terrain becomes empty after flying far from the player:** on multiplayer, the server sends chunks around the real player, not the client-only camera. FreeCam can render only chunks already received by the client. Increase the server/client view distance or keep the camera within the loaded area; Veyra deliberately does not spoof player movement to request more chunks.

If a renderer profile behaves incorrectly, compare against the other profile. The OpenGL profile includes Sodium/Iris and additional culling mods; the Vulkan profile intentionally stays lean.

## The wrong RAM value is used

The Settings memory slider is persisted from 2-16 GB. On launch, the backend removes inherited `-Xmx`/`-Xms` values and supplies its own heap flags. The Logs view should contain `Allocated memory: <MB> MB`; the Java process should contain the matching `-Xmx<MB>M` argument.

## Resolution is wrong

Auto resolution reads the primary monitor's current logical size and passes it to Minecraft. Choose Custom resolution if display scaling, remote desktop, or a multi-monitor arrangement reports an unexpected value. Fullscreen behavior is controlled separately.

## Authentication fails

- Complete the Microsoft window without closing it early.
- Confirm the account owns Minecraft Java Edition.
- Sign out and sign in again if the saved refresh token is invalid.
- Account metadata is under the Veyra data directory; refresh tokens are stored in the OS keyring when supported.

## Managed mods conflict

Each renderer has its own profile folder. Put manual jars in the profile-specific mods folder opened from the launcher, not only in the active `minecraft/mods` directory, because launch synchronization mirrors the selected profile into the active folder.

Avoid adding OpenGL renderer replacements to the Vulkan profile or VulkanMod to the OpenGL profile.

## Useful verification commands

```powershell
git diff --check

cd launcher
npm.cmd run build
cargo check --manifest-path src-tauri\Cargo.toml

cd ..\mod
gradle build
```
