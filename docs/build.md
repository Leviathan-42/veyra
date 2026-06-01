# Build / Run Notes

## Launcher

Path: `launcher/`

Install dependencies:

```bash
npm install
```

Run in dev mode:

```bash
npm run tauri dev
```

Build app bundle:

```bash
npm run tauri build
```

## Mod

Path: `mod/`

The mod build currently compiles against Minecraft jars downloaded by the Veyra launcher. Build/run the launcher once and install the target Minecraft version first. The launcher now defaults to the latest stable release instead of snapshots.

Target version is configured in:

```text
mod/gradle.properties
```

Current values:

```properties
minecraft_version=26.1.2
loader_version=0.19.2
mod_version=0.1.0
```

Build:

```bash
gradle build
```

If Gradle wrapper is added later, prefer:

```bash
./gradlew build
```

## Cross-OS data dir override

`mod/build.gradle` resolves the launcher data directory automatically:

- Windows: `%APPDATA%/VeyraLauncher`
- macOS: `~/Library/Application Support/VeyraLauncher`
- Linux: `$XDG_DATA_HOME/VeyraLauncher` or `~/.local/share/VeyraLauncher`

You can override it:

```bash
gradle build -PveyraDataDir=/path/to/data/dir
```

## Recommended future build cleanup

The mod should eventually use Fabric Loom instead of compiling directly against launcher-downloaded jars. Loom would make dependency resolution cleaner and more standard across OSes.
