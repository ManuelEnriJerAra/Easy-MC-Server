# Server Start Silent Crash Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-start-silent-crash.md`

## Scope

Harden the server start flow when startup fails before Minecraft can write useful logs. This covers start button actions, context-menu start actions, process launch failures, uncaught app exceptions, and server-state notification failures.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect start path | Reviewed `PanelControlServidor`, `PanelServidores`, `GestorServidores.iniciarServidor(...)`, process launch, and state notifications. |
| DONE | 2. Identify silent crash risks | Found start actions rethrowing exceptions on the Swing event thread and process launch failures being escalated after already writing a console error. |
| DONE | 3. Add diagnostics | Added `AppErrorReporter` with a global uncaught exception handler and persistent app error log. |
| DONE | 4. Harden start failures | Start actions now report errors and write to the server console instead of rethrowing on the EDT; process launch failures are logged instead of escalated. |
| DONE | 5. Verify behavior | Compiled after changes. |

## Implementation Notes

Minecraft server logs only exist after the child process starts and emits output. Failures before or during `ProcessBuilder.start()` can be app-side failures with no server log entry. The start UI must not rethrow these on the Swing event thread.

Uncaught app exceptions are now written to:

- `%USERPROFILE%\.easy-mc-server\logs\easy-mc-errors.log`

This gives future crash reports a durable place to inspect even when the visible UI disappears.

## Verification Notes

- `mvn -q -DskipTests compile` passed.
- Maven emitted the expected Lombok/Guice `sun.misc.Unsafe` warning.
