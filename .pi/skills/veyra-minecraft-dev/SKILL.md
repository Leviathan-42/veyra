---
name: veyra-minecraft-dev
description: Restart and relaunch Veyra Minecraft 26.1.2 for shaderpack compatibility testing, quick-play a singleplayer world, wait for the Iris compatibility report, then inspect/report errors. Use when working on Solas/Iris/VulkanMod shader pipeline changes that need a Minecraft restart.
---

# Veyra Minecraft Dev Cycle

Use this skill when Java mod code was rebuilt and Minecraft must be restarted to load the new jar.

## Script

Run from the repository root:

```bash
./tools/veyra_minecraft_dev_cycle.py --latest-world --wait-report
```

Useful flags:

- `--skip-build` if the mod was already built and copied is enough.
- `--world "New World (2)"` to select a specific singleplayer save.
- `--latest-world` to use the most recently modified save.
- `--wait-report --timeout 240` to wait for `config/veyra-iris-compat-report.txt` to update.
- `--no-kill` only for dry launch tests; normally let it stop the old game first.

The script:

1. runs `gradle build` in `mod/` unless `--skip-build` is supplied,
2. copies `mod/build/libs/block-tracker-0.1.0.jar` into the managed mods folder,
3. terminates the existing Veyra Minecraft Java process,
4. launches Minecraft `26.1.2` directly with Fabric/VulkanMod using the Veyra account file,
5. passes `--quickPlaySingleplayer <world>` when a world is found/provided,
6. writes game output to `~/Library/Application Support/VeyraLauncher/minecraft/logs/veyra-dev-cycle.log`,
7. waits for `~/Library/Application Support/VeyraLauncher/minecraft/config/veyra-iris-compat-report.txt` when requested.

Never print the full Java command because it contains the Minecraft access token.

After the script reports an updated report, read only the report sections needed for SPIR-V compile blockers and patch `mod/src/main/java/dev/blocktracker/shader/IrisSourceTransformer.java` or the render pipeline wiring.
