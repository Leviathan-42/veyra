#!/usr/bin/env python3
"""Build/restart Veyra Minecraft and optionally quick-play a singleplayer world.

This is a dev-only harness for shader compatibility work. It intentionally lives
outside the shipped launcher/mod path and does not add user-facing Minecraft UI.
"""
from __future__ import annotations

import argparse
import json
import os
import plistlib
import shutil
import signal
import subprocess
import sys
import time
import urllib.request
from pathlib import Path
from typing import Any

HOME = Path.home()
PROJECT = Path(__file__).resolve().parents[1]
GAME_DIR = HOME / "Library/Application Support/VeyraLauncher/minecraft"
ACCOUNT_FILE = HOME / "Library/Application Support/VeyraLauncher/account.json"
MOD_JAR = PROJECT / "mod/build/libs/block-tracker-0.1.0.jar"
INSTALLED_MOD_JAR = GAME_DIR / "mods/block-tracker-0.1.0.jar"
REPORT = GAME_DIR / "config/veyra-iris-compat-report.txt"
LOG = GAME_DIR / "logs/veyra-dev-cycle.log"


def sh(cmd: list[str], cwd: Path | None = None, quiet: bool = False) -> subprocess.CompletedProcess[str]:
    if not quiet:
        print("$", " ".join(cmd), flush=True)
    return subprocess.run(cmd, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True)


def iris_bool_prop(env_name: str, prop_name: str) -> list[str]:
    value = os.environ.get(env_name)
    if value is None or value == "":
        return []
    if value.lower() in {"1", "true", "yes", "on"}:
        return [f"-D{prop_name}=true"]
    if value.lower() in {"0", "false", "no", "off"}:
        return [f"-D{prop_name}=false"]
    return []


def java_path() -> str:
    candidates = [
        "/opt/homebrew/opt/java/bin/java",
        "/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home/bin/java",
        shutil.which("java") or "java",
    ]
    for candidate in candidates:
        if candidate == "java" or Path(candidate).exists():
            return candidate
    return "java"


def latest_world() -> str | None:
    saves = GAME_DIR / "saves"
    if not saves.exists():
        return None
    worlds = [p for p in saves.iterdir() if p.is_dir() and p.name != "saves"]
    if not worlds:
        return None
    worlds.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return worlds[0].name


def kill_minecraft(grace: float = 5.0) -> None:
    try:
        out = subprocess.check_output(["pgrep", "-fl", str(GAME_DIR)], text=True)
    except subprocess.CalledProcessError:
        print("No running Veyra Minecraft process found.")
        return

    pids: list[int] = []
    for line in out.splitlines():
        parts = line.split(maxsplit=1)
        if not parts:
            continue
        pid = int(parts[0])
        cmd = parts[1] if len(parts) > 1 else ""
        if "GradleDaemon" in cmd or "veyra_minecraft_dev_cycle" in cmd:
            continue
        if "net.fabricmc" in cmd or "minecraft" in cmd.lower() or "KnotClient" in cmd:
            pids.append(pid)

    if not pids:
        print("No running Veyra Minecraft process found.")
        return

    print("Stopping Minecraft:", ", ".join(map(str, pids)))
    for pid in pids:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass

    deadline = time.time() + grace
    while time.time() < deadline:
        alive = []
        for pid in pids:
            try:
                os.kill(pid, 0)
                alive.append(pid)
            except ProcessLookupError:
                pass
        if not alive:
            return
        time.sleep(0.25)

    for pid in pids:
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


def build_mod(skip_build: bool) -> None:
    if not skip_build:
        result = sh(["gradle", "build"], cwd=PROJECT / "mod")
        if result.stdout.strip():
            print(result.stdout)
    INSTALLED_MOD_JAR.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(MOD_JAR, INSTALLED_MOD_JAR)
    print(f"Installed {INSTALLED_MOD_JAR}")


def allowed(rule: dict[str, Any], features: dict[str, bool]) -> bool:
    action = rule.get("action", "allow")
    os_rule = rule.get("os") or {}
    os_name = os_rule.get("name")
    os_ok = os_name in (None, "osx")
    feat_ok = all(features.get(k, False) == v for k, v in (rule.get("features") or {}).items())
    matches = os_ok and feat_ok
    return matches if action == "allow" else not matches


def rules_allow(rules: list[dict[str, Any]] | None, features: dict[str, bool]) -> bool:
    if not rules:
        return True
    result = False
    for rule in rules:
        if allowed(rule, features):
            result = rule.get("action", "allow") == "allow"
    return result


def append_arg(value: Any, args: list[str], repl, features: dict[str, bool]) -> None:
    if isinstance(value, str):
        args.append(repl(value))
    elif isinstance(value, dict):
        if not rules_allow(value.get("rules"), features):
            return
        v = value.get("value")
        if isinstance(v, str):
            args.append(repl(v))
        elif isinstance(v, list):
            for item in v:
                if isinstance(item, str):
                    args.append(repl(item))


def artifact_path(game_dir: Path, lib: dict[str, Any]) -> Path | None:
    downloads = lib.get("downloads") or {}
    artifact = downloads.get("artifact") or {}
    rel = artifact.get("path")
    if rel:
        return game_dir / "libraries" / rel
    name = lib.get("name", "")
    parts = name.split(":")
    if len(parts) != 3:
        return None
    group, artifact_name, version = parts
    return game_dir / "libraries" / Path(group.replace(".", "/")) / artifact_name / version / f"{artifact_name}-{version}.jar"


def maven_path(coordinate: str) -> Path:
    parts = coordinate.split(":")
    if len(parts) < 3:
        raise ValueError(f"Invalid Maven coordinate: {coordinate}")
    group, artifact, version = parts[:3]
    classifier = parts[3] if len(parts) > 3 else None
    filename = f"{artifact}-{version}-{classifier}.jar" if classifier else f"{artifact}-{version}.jar"
    return Path(group.replace(".", "/")) / artifact / version / filename


def fabric_runtime_jars(game_dir: Path, version_id: str) -> tuple[str, list[Path]]:
    url = f"https://meta.fabricmc.net/v2/versions/loader/{version_id}"
    with urllib.request.urlopen(url, timeout=15) as response:
        versions = json.loads(response.read().decode("utf-8"))
    selected = next((v for v in versions if v.get("loader", {}).get("stable")), versions[0])
    meta = selected["launcherMeta"]
    libs = [
        selected["loader"]["maven"],
        selected["intermediary"]["maven"],
        *[lib["name"] for lib in meta.get("libraries", {}).get("common", [])],
        *[lib["name"] for lib in meta.get("libraries", {}).get("client", [])],
    ]
    jars = [game_dir / "libraries" / maven_path(lib) for lib in libs]
    missing = [str(path) for path in jars if not path.exists()]
    if missing:
        raise FileNotFoundError("Missing Fabric libraries. Launch once from Veyra launcher first: " + ", ".join(missing[:4]))
    main_class = meta["mainClass"]
    if isinstance(main_class, dict):
        main_class = main_class.get("client")
    return str(main_class), jars


def build_launch_args(version_id: str, world: str | None, width: int, height: int, fullscreen: bool) -> list[str]:
    version_path = GAME_DIR / "versions" / version_id / f"{version_id}.json"
    client_jar = GAME_DIR / "versions" / version_id / f"{version_id}.jar"
    natives_dir = GAME_DIR / "versions" / version_id / "natives"
    version = json.loads(version_path.read_text())
    account = json.loads(ACCOUNT_FILE.read_text())
    profile = account["profile"]

    fabric_main_class, fabric_cp = fabric_runtime_jars(GAME_DIR, version_id)
    cp: list[Path] = []
    for lib in version.get("libraries", []):
        if not rules_allow(lib.get("rules"), {}):
            continue
        p = artifact_path(GAME_DIR, lib)
        if p and p.exists():
            cp.append(p)
    cp.extend(fabric_cp)
    cp.append(client_jar)
    cp_str = ":".join(str(p) for p in dict.fromkeys(cp))

    features = {
        "is_demo_user": False,
        "has_custom_resolution": True,
        "has_quick_plays_support": bool(world),
        "is_quick_play_singleplayer": bool(world),
        "is_quick_play_multiplayer": False,
        "is_quick_play_realms": False,
    }

    def common(s: str) -> str:
        return (
            s.replace("${natives_directory}", str(natives_dir))
            .replace("${launcher_name}", "VeyraDevCycle")
            .replace("${launcher_version}", "0.1.0")
            .replace("${classpath}", cp_str)
        )

    def game(s: str) -> str:
        return (
            common(s)
            .replace("${auth_player_name}", profile["name"])
            .replace("${version_name}", version_id)
            .replace("${game_directory}", str(GAME_DIR))
            .replace("${assets_root}", str(GAME_DIR / "assets"))
            .replace("${assets_index_name}", version["assetIndex"]["id"])
            .replace("${auth_uuid}", profile["id"])
            .replace("${auth_access_token}", account["access_token"])
            .replace("${clientid}", "")
            .replace("${auth_xuid}", "")
            .replace("${user_type}", "msa")
            .replace("${version_type}", "snapshot")
            .replace("${quickPlayPath}", str(GAME_DIR / "quickPlay"))
            .replace("${quickPlaySingleplayer}", world or "")
        )

    args: list[str] = []
    for value in (version.get("arguments") or {}).get("jvm", []):
        append_arg(value, args, common, features)
    if "-cp" not in args and "-classpath" not in args:
        args.extend(["-Djava.library.path=" + str(natives_dir), "-cp", cp_str])

    args.extend([
        "-Dveyra.dev.cycle=true",
        "-Dveyra.dev.shaderpack=Solas Shader V3.6",
        "-Dveyra.iris.compileCheckLimit=256",
        *iris_bool_prop("VEYRA_IRIS_EXECUTE_GRAPH", "veyra.iris.executeGraph"),
        *(["-Dveyra.iris.executeGraph.maxPasses=" + os.environ["VEYRA_IRIS_EXECUTE_MAX"]] if os.environ.get("VEYRA_IRIS_EXECUTE_MAX") else []),
        *iris_bool_prop("VEYRA_IRIS_EXECUTE_DRAW", "veyra.iris.executeGraph.draw"),
        *iris_bool_prop("VEYRA_IRIS_PRESENT_TO_SCREEN", "veyra.iris.presentToScreen"),
        *iris_bool_prop("VEYRA_IRIS_PRESENT_SHADER_FINAL", "veyra.iris.presentShaderFinal"),
        *(["-Dveyra.iris.presentSource=" + os.environ["VEYRA_IRIS_PRESENT_SOURCE"]] if os.environ.get("VEYRA_IRIS_PRESENT_SOURCE") else []),
        "--enable-native-access=ALL-UNNAMED",
        fabric_main_class,
    ])

    for value in (version.get("arguments") or {}).get("game", []):
        append_arg(value, args, game, features)

    args.extend(["--width", str(width), "--height", str(height)])
    if fullscreen:
        args.append("--fullscreen")
    if world and "--quickPlaySingleplayer" not in args:
        args.extend(["--quickPlaySingleplayer", world])

    return args


def launch(version: str, world: str | None, width: int, height: int, fullscreen: bool, dry_run: bool = False) -> int:
    LOG.parent.mkdir(parents=True, exist_ok=True)
    args = build_launch_args(version, world, width, height, fullscreen)
    cmd = [java_path(), *args]
    if dry_run:
        print(f"Launch command prepared with {len(cmd)} arguments. Not printing it because it contains the access token.")
        return 0
    with LOG.open("ab") as log:
        log.write(f"\n\n--- Veyra dev launch {time.ctime()} ---\n".encode())
        proc = subprocess.Popen(cmd, cwd=GAME_DIR, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
    print(f"Started Minecraft pid={proc.pid}; log={LOG}")
    if world:
        print(f"Quick-play world: {world}")
    return proc.pid


def wait_report(since: float, timeout: float) -> None:
    deadline = time.time() + timeout
    print(f"Waiting for updated report: {REPORT}")
    while time.time() < deadline:
        if REPORT.exists() and REPORT.stat().st_mtime >= since:
            print("Report updated.")
            return
        time.sleep(1)
    print("Timed out waiting for report.")


def main() -> int:
    parser = argparse.ArgumentParser(description="Restart Veyra Minecraft for shader dev.")
    parser.add_argument("--version", default="26.1.2")
    parser.add_argument("--world", default=os.environ.get("VEYRA_DEV_WORLD"))
    parser.add_argument("--latest-world", action="store_true", help="Use most recently modified singleplayer save.")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--no-kill", action="store_true")
    parser.add_argument("--width", type=int, default=1280)
    parser.add_argument("--height", type=int, default=720)
    parser.add_argument("--fullscreen", action="store_true")
    parser.add_argument("--wait-report", action="store_true")
    parser.add_argument("--timeout", type=float, default=180.0)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    world = args.world
    if args.latest_world or not world:
        world = latest_world()

    if not ACCOUNT_FILE.exists():
        print(f"Missing account file: {ACCOUNT_FILE}. Launch once from Veyra after signing in.", file=sys.stderr)
        return 2

    started_at = time.time()
    build_mod(args.skip_build)
    if not args.no_kill:
        kill_minecraft()
    launch(args.version, world, args.width, args.height, args.fullscreen, args.dry_run)
    if args.wait_report and not args.dry_run:
        wait_report(started_at, args.timeout)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
