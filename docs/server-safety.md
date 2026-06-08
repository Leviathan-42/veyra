# Server Safety / Rules

The current Veyra mod code is client-side.

It reads client-known data and draws local overlays. It does not currently implement custom packet sending, reach changes, player movement changes, combat automation, inventory automation, or rotation spoofing.

## Current client-side behavior

Current features include:

- scanning loaded client chunks for blocks, currently up to a 12-chunk radius from the player
- drawing block ESP/tracers locally
- drawing entity boxes locally
- local HUD labels
- local config/search screens
- durability/effect/FPS/ping/RAM/coordinate HUD
- local waypoints and death marker
- optional local fullbright lightmap adjustment
- local freecam camera movement that cancels player movement input while active

From the code currently present, normal gameplay packets should remain vanilla unless future features change that. It should be able to connect to vanilla servers because the server does not need to install Veyra.

## Important caveat

Client-side does **not** mean allowed.

Servers can ban mods or behavior by rule even when the server cannot directly inspect the overlay. Risk can still come from:

- staff watching gameplay
- suspicious mining/navigation/combat behavior
- required-client or allowed-mod policies
- screenshots/streams/clips
- future features that alter gameplay or networking

## Avoid for public-server safety

Do not add features that alter or automate gameplay if the goal is to stay within normal client behavior:

- reach changes
- speed/fly/movement changes
- aim assist/kill aura
- auto-clicking
- scaffold/bridging automation
- nuker or block automation
- inventory automation
- packet manipulation
- rotation spoofing

Use Veyra only where the server rules allow it.
