# Data Layout

Veyra stores launcher data in the OS user data directory.

## Launcher app dir

Current app dir name:

```text
VeyraLauncher
```

Legacy app dir name:

```text
BlockTrackerLauncher
```

If the legacy dir exists, the launcher attempts to rename it to `VeyraLauncher`.

## OS locations

Typical locations:

### Windows

```text
%APPDATA%/VeyraLauncher
```

### macOS

```text
~/Library/Application Support/VeyraLauncher
```

### Linux

```text
$XDG_DATA_HOME/VeyraLauncher
```

or, if `XDG_DATA_HOME` is unset:

```text
~/.local/share/VeyraLauncher
```

## Game directory

```text
<VeyraLauncher>/minecraft
```

Contains:

- `versions/`
- `libraries/`
- `assets/`
- `mods/`

## Mods directory

```text
<VeyraLauncher>/minecraft/mods
```

The launcher UI can open this folder and list installed `.jar` files.

## Auth/account data

Account metadata path:

```text
<VeyraLauncher>/account.json
```

Refresh token storage uses the OS keyring where supported.

Keyring service/user:

```text
dev.blocktracker.launcher / minecraft_refresh_token
```
