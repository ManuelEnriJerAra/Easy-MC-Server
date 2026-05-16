# Server Start Silent Crash

## Status

Fixed

## Original Issue

Starting a server could make the app crash or appear to crash without a useful Minecraft log message.

## Root Cause

Some failures happen before Minecraft writes logs, especially process launch failures or app-side listener/state errors. The start button and server-list context menu rethrew startup exceptions on the Swing event thread, and process launch failures were escalated after already writing an error line.

## Solution

The start flow now handles startup failures without rethrowing on the UI thread:

- start button and context-menu start actions report errors and append a server console line;
- `GestorServidores.iniciarServidor(...)` logs `ProcessBuilder.start()` failures instead of throwing a runtime exception;
- server-state notification failures are reported instead of escaping;
- `AppErrorReporter` installs a global uncaught exception handler and writes app errors to `%USERPROFILE%\.dora\logs\dora-errors.log`.

## Files Changed

- `src/main/java/controlador/AppErrorReporter.java`
- `src/main/java/controlador/Main.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/vista/PanelControlServidor.java`
- `src/main/java/vista/PanelServidores.java`
- `docs/fixes/process/server-start-silent-crash.md`

## Verification

- `mvn -q -DskipTests compile`

Passed. Maven emitted the expected Lombok/Guice `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/fixes/process/server-start-silent-crash.md`

## Regression Notes

Startup code should append actionable errors to the app/server console and persistent app log rather than throwing unchecked exceptions on the EDT. Minecraft logs cannot explain failures that occur before the child process starts.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/console-and-players-pipeline.md`
