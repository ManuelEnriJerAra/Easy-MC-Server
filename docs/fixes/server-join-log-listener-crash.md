# Server Join Log Listener Crash

## Status

Fixed

## Original Issue

The app could crash or appear to freeze when a server was running and a player joined, with no useful Minecraft server log message explaining the failure.

## Root Cause

Player joins are delivered through the server console listener pipeline. `Server.appendConsoleLinea(...)` notified listeners while holding the synchronized server model lock and did not isolate runtime failures from individual listeners. A bad UI listener could interrupt the log-reader path and prevent later listeners or diagnostics from running.

## Solution

`Server.appendConsoleLinea(...)` now:

- stores the normalized log line and raw log history before listener delivery;
- snapshots listeners while synchronized, then releases the lock;
- catches runtime failures from each listener so one panel cannot break the whole log fan-out.

`ServerLogReader` now catches runtime failures from log processing and attempts to report them.

Player join UI now also skips Mojang head lookups for names that are not valid Java usernames, which covers common Bedrock/Floodgate-prefixed names. Console and player-list EDT updates are wrapped so a malformed join line or UI rendering failure is logged instead of taking down the app.

Follow-up hardening for the repeated report also addresses freeze-style failures:

- console log lines are batched through one bounded EDT drain instead of scheduling one Swing task per line;
- console trimming removes old rendered lines incrementally instead of rebuilding the full 5000-line document on every overflow;
- raw server log storage now appends/trims through a deque-backed queue;
- join/leave log lines update the visible player set without rereading whitelist, ban, and OP files on the EDT;
- process exit/restart cleanup is wrapped so runtime failures are reported.

## Files Changed

- `src/main/java/modelo/Server.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/controlador/ServerLogReader.java`
- `src/main/java/vista/PanelConsola.java`
- `src/main/java/vista/PanelJugadores.java`
- `src/main/java/vista/PlayerIdentityView.java`
- `src/test/java/modelo/ServerModelPlatformTest.java`
- `docs/fixes/process/server-join-log-listener-crash.md`

## Verification

- `mvn -q -Dtest=ServerModelPlatformTest test`
- `mvn -q "-Dtest=ModrinthExtensionCatalogProviderTest,ServerModelPlatformTest" test`
- `mvn -q -DskipTests compile`

Both passed. The targeted test intentionally prints one isolated listener failure to stderr. Maven emitted the expected Lombok/Guice `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/fixes/process/server-join-log-listener-crash.md`

## Regression Notes

Console listeners should never be invoked while holding the server model lock, and one listener should not be able to stop delivery to other panels. Join/leave handling should remain resilient because multiple UI panels consume the same log lines.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/console-and-players-pipeline.md`
