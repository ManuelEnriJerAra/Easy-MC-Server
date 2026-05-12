# Console And Players Pipeline

Use this guide before editing live console rendering, command input, connected-player tracking, known users, whitelist/ban/op UI, or player debug controls.

## Console Pipeline

Core classes:

- `controlador.ServerLogReader`
- `modelo.Server`
- `vista.PanelConsola`

Flow:

1. Server process starts.
2. `ServerLogReader` reads stdout/stderr lines.
3. Lines are forwarded to server console listeners.
4. `PanelConsola.escribirLinea(...)` stores raw lines and appends styled output.
5. Simple view can translate/simplify noisy Minecraft log lines.

`PanelConsola` also tracks join/left/chat patterns to update connected player state.

## Command Input

Commands typed in `PanelConsola` are sent to the running server process through the server manager/runtime path.

Keep command sending disabled or harmless when no server process is alive.

## Player Panel

Main class: `vista.PanelJugadores`.

Responsibilities:

- Render connected players.
- Track real players from logs.
- Merge debug fake players while Debug mode is enabled.
- Open and edit lists such as whitelist, banned players, banned IPs, and OPs.
- Sync known users and remote Mojang profile data where available.

## List Files

Player management touches server list files such as:

- `whitelist.json`
- `ops.json`
- `banned-players.json`
- `banned-ips.json`

Be careful when editing JSON list structures. Preserve unrelated fields where possible.

## Known User Data

`GestorUsuariosConocidos` maintains known user identities. Use it instead of inventing separate username caches.

## Debug Players

`PanelJugadores` is the example debug implementation:

- debug plus/minus buttons appear in header actions
- fake players are in-memory only
- fake players clear when Debug mode is disabled

See `debug-mode-pipeline.md`.

## Tests

Relevant tests are lighter here. Always compile after UI changes. Add focused tests if moving parsing or file-writing behavior out of UI code.
