# Server Join Log Listener Crash Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-join-log-listener-crash.md`

## Scope

Investigate and harden the path where Dora can crash or appear to freeze when a running server emits player join lines. This covers server process log reading, console listener fan-out, and player-panel listeners. It does not change Minecraft server startup commands or player list file editing behavior.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked server lifecycle and console/player pipeline docs. |
| DONE | 2. Inspect crash-adjacent paths | Reviewed `ServerLogReader`, `Server.appendConsoleLinea(...)`, `PanelConsola`, `PanelJugadores`, Mojang player-head loading, and stats player counting. |
| DONE | 3. Implement listener hardening | Moved console listener notification outside the synchronized server block, snapshot listeners before delivery, and isolate listener runtime failures. |
| DONE | 4. Harden join UI consumers | Wrapped player/console EDT updates and skipped Mojang head lookups for non-Java usernames such as Bedrock/Floodgate-prefixed names. |
| DONE | 5. Add regression coverage | Added a model test proving a broken listener does not prevent later listeners or raw-log persistence. |
| DONE | 6. Audit repeated freeze report | Used parallel review to inspect join/start paths and identified EDT saturation from console rendering plus join-triggered list file reads. |
| DONE | 7. Reduce join-time EDT load | Batched console rendering, switched raw-log trimming away from `ArrayList.remove(0)`, stopped join/leave events from reloading player list files, and cached OP names from the normal counter refresh. |
| DONE | 8. Harden process exit callback | Wrapped server `onExit` cleanup/restart handling so runtime failures are reported instead of silently ending the callback. |
| DONE | 9. Verify behavior | Ran targeted tests and compile. |

## Implementation Notes

Player joins are processed through console listeners. Before this fix, `Server.appendConsoleLinea(...)` held the `Server` monitor while notifying every listener directly. Any runtime exception from one UI listener could abort the remaining notifications and could also escape into `ServerLogReader`, making log processing stop with little visible context.

The fix stores the normalized log line in the buffer/raw-log history first, copies the listener list, releases the server lock, then notifies listeners individually. A failing listener is reported to stderr and does not stop other panels from receiving the line.

`ServerLogReader` now also catches runtime failures from log processing and attempts to surface them as console errors.

Join handling can also create player identity cards. Those cards now keep non-Java usernames as initial-only avatars instead of calling Mojang skin/profile endpoints, and EDT callbacks around player list and console rendering report failures through `AppErrorReporter`.

The follow-up audit found that the app could still appear to crash when a join produced a burst of startup/plugin logs. `PanelConsola` previously queued one EDT task per log line and rebuilt the full 5000-line styled document whenever the raw log buffer trimmed. Console updates now share one pending queue, drain in bounded EDT batches, and trim visible document lines incrementally.

Join and leave lines no longer call `recargarContadores()` directly. That method reads `whitelist.json`, `banned-players.json`, `banned-ips.json`, and `ops.json`, so doing it on every join/leave could freeze Swing if the files were slow or locked. OP names are loaded during the normal counter refresh and reused while rendering player cards.

The server raw-log history now uses a deque-backed queue for new entries, avoiding repeated `ArrayList.remove(0)` shifts once the history reaches 5000 lines.

## Verification Notes

- `mvn -q -Dtest=ServerModelPlatformTest test` passed. The test intentionally prints one isolated listener failure to stderr.
- `mvn -q "-Dtest=ModrinthExtensionCatalogProviderTest,ServerModelPlatformTest" test` passed. The same intentional listener failure is printed by the model test.
- `mvn -q -DskipTests compile` passed.
- Maven emitted the expected Lombok/Guice `sun.misc.Unsafe` warning.
