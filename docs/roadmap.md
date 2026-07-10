# Roadmap

The major launcher UI, custom Minecraft UI/boot sequence, incremental scanner, queued targets, modular telemetry HUD and presets, profile management, RAM control, auto resolution, and FreeCam camera integration are implemented. The remaining roadmap focuses on maintainability and product finish.

## Build and release

- add and pin a Gradle wrapper;
- migrate the mod build to Fabric Loom instead of compiling against launcher-installed jars;
- add CI for Vite, Cargo, Gradle, `git diff --check`, and artifact hashes;
- create signed/versioned launcher bundles;
- add release notes and an explicit mod/launcher compatibility table.

## Testing

- unit-test JVM argument replacement and memory clamping;
- test profile mirroring with conflicting/manual jars;
- test block scan cancellation, replacement, chunk unloads, and retry behavior;
- add persistence round-trip tests for `veyra-client.properties`;
- exercise FreeCam across vanilla, Sodium/Iris, and Vulkan profiles;
- add screenshot-based smoke checks for the launcher and custom Minecraft screens.

## UX polish

- replace the temporary V mark with final SVG/PNG branding and platform icons;
- persist renderer, version, Java, fullscreen, and display selections;
- show validation for unreasonable custom resolutions before launch;
- add import/export/reset actions for Veyra client settings;
- improve keyboard/controller navigation and screen-reader labels.

## Architecture

- split the large launcher component into focused Svelte components/stores;
- extract managed-mod profile definitions from `minecraft.rs` into data/config;
- centralize shared version constants so docs, Fabric metadata, Gradle, and UI cannot drift;
- add structured logging categories instead of relying only on text messages;
- plan a migration before renaming stable `blocktracker` identifiers.

## Compatibility

- document the supported renderer-mod matrix per release;
- degrade gracefully when an optional renderer/culling integration changes internals;
- keep FreeCam client-only and clearly expose the server-chunk visibility limit;
- review new Minecraft releases before changing the launcher's default version.
