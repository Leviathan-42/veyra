# Server safety and rules

Veyra is implemented as a client-only Fabric mod. A vanilla server does not need Veyra installed for the client to connect.

## Current technical boundary

The mod currently:

- scans block data already loaded on the client with a bounded per-tick budget;
- renders local block/entity boxes, tracers, labels, waypoints, and HUD modules;
- displays local performance, ping history, self-status warnings, music, time, keystrokes, and CPS telemetry;
- stores local waypoints, death marker, appearance, and HUD settings;
- adjusts the client lightmap for optional fullbright;
- replaces local title/settings/search/tutorial screens;
- uses a client-only marker entity for the FreeCam viewpoint; and
- clears player movement input while FreeCam is active.

It does not currently implement custom gameplay packets, reach changes, combat or inventory automation, movement-speed changes, rotation spoofing, or server-required content.

FreeCam deliberately leaves the real player in place and therefore does not request server chunks around the detached camera. On multiplayer, visible terrain remains limited to data sent around the actual player.

## Rules still apply

Client-only does not mean permitted. A server can prohibit overlays, FreeCam, fullbright, block search, or all non-approved clients. Enforcement may be based on behavior, required-client policies, screenshots/streams, or staff review rather than technical packet detection.

Use Veyra only in singleplayer, on private/testing servers, or where the server's rules explicitly allow the features you enable.

## Features to avoid if this boundary matters

Do not add:

- reach, speed, flight, or knockback modification;
- aim assist, kill aura, trigger bots, or auto-clicking;
- scaffold, nuker, or automated block interaction;
- inventory automation;
- packet cancellation/injection intended to change gameplay;
- movement/rotation spoofing; or
- server chunk requests driven by the detached FreeCam camera.

Any future feature that sends packets or changes networked player state must update this document and be reviewed as a separate compatibility/security boundary.
