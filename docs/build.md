# Build and development

## Prerequisites

- Node.js with npm
- Rust stable and Cargo
- Java Development Kit 25
- Gradle 9.x
- Windows WebView2 for Tauri development on Windows

The mod compiles against the Minecraft jar and libraries installed by Veyra, so launch the configured Minecraft version through the launcher at least once before the first mod build.

## Launcher frontend

```powershell
cd launcher
npm install
npm.cmd run build
```

Use `npm.cmd` on Windows if PowerShell prevents `npm.ps1` from running.

## Launcher desktop development

```powershell
cd launcher
npm run tauri dev
```

This starts Vite and the Tauri development shell. A Rust-only type/build check is:

```powershell
cargo check --manifest-path src-tauri\Cargo.toml
```

Build distributable desktop bundles with:

```powershell
npm run tauri build
```

The Tauri build runs the Vite production build automatically through `beforeBuildCommand`.

For the Windows x64 NSIS installer, build the mod resource first and package everything with:

```powershell
cd launcher
npm.cmd run bundle:windows
```

The installer is written to `launcher/src-tauri/target/release/bundle/nsis/`. It embeds the Veyra mod and WebView2 bootstrapper; Minecraft, Fabric, renderer-profile mods, and managed Java are provisioned online as needed.

## Publishing a GitHub release

Release installers are currently built and smoke-tested on Windows, then uploaded directly to a GitHub release. The former tag-triggered matrix workflow was removed because it attempted NSIS packaging on macOS and Linux and could not build the bundled mod on a clean runner.

After the source commit is on `main`, create the matching tag/release and attach the verified installer:

```powershell
gh release create v0.1.0 `
  "src-tauri\target\release\bundle\nsis\Veyra Launcher_0.1.0_x64-setup.exe" `
  --title "Veyra Launcher 0.1.0" `
  --generate-notes
```

Reintroduce automated release packaging only after the mod build can provision its Minecraft compile dependencies deterministically on a clean GitHub runner.

## Fabric mod

Current coordinates are in `mod/gradle.properties`:

```properties
minecraft_version=26.2
loader_version=0.19.3
mod_version=0.1.0
```

Build:

```powershell
cd mod
gradle build
```

Output:

```text
mod/build/libs/block-tracker-0.1.0.jar
```

The repository currently does not include a Gradle wrapper. Use an installed Gradle 9.x distribution. Adding a checked-in wrapper is tracked in the roadmap.

## Launcher data override for mod builds

`mod/build.gradle` resolves the Veyra data directory by operating system:

- Windows: `%APPDATA%/VeyraLauncher`
- macOS: `~/Library/Application Support/VeyraLauncher`
- Linux: `$XDG_DATA_HOME/VeyraLauncher` or `~/.local/share/VeyraLauncher`

Override it when building against a different managed instance:

```powershell
gradle build -PveyraDataDir=C:\path\to\data
```

## Development jar synchronization

On launch, Rust checks for `mod/build/libs/block-tracker-0.1.0.jar`, removes older `block-tracker-*.jar` files from the managed profile/active mod set, and copies the current build into the instance. Restart Minecraft after rebuilding; an already-running JVM cannot reload the jar.

## Verification checklist

Run before handing off a change:

```powershell
git diff --check

cd launcher
npm.cmd run build
cargo check --manifest-path src-tauri\Cargo.toml

cd ..\mod
gradle build
```

For runtime-sensitive mod changes:

1. copy/sync the built jar;
2. launch through the newly built Tauri backend;
3. exercise the affected Minecraft screen or feature;
4. close the test instance cleanly;
5. inspect `<game dir>/logs/latest.log` for mixin/application exceptions; and
6. compare SHA-256 hashes for the built and installed jars.

## Source formatting and scope

- Preserve user changes in the working tree.
- Keep mixins narrow; prefer behavior in ordinary classes that can be compiled and reasoned about directly.
- Keep UI primitives centralized in `VeyraUi` and launcher theme tokens in `styles.css`.
- Document new settings, Tauri commands, keybinds, storage files, and compatibility limitations in the same change.
