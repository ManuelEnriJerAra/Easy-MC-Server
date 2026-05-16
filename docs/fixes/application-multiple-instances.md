# Application Multiple Instances Fix

## Status

Fixed

## Original Issue

Dora could be launched multiple times at once. Separate instances could read and write the same managed server list, server configuration, logs, process state, and UI-derived caches independently.

## Root Cause

`controlador.Main` started the Swing application without acquiring any per-user/process guard before loading configuration, initializing controllers, or opening the main window.

## Solution

Added `controlador.ApplicationInstanceLock`, which acquires an OS-level `FileLock` under `AppPaths.locksDirectory()`. `Main` now acquires this lock before scheduling Swing startup. The first instance also starts a localhost handoff listener. If another instance already holds the lock, the second launch sends a focus request to the running app and exits without creating `GestorServidores` or application windows.

The lock channel remains open through a static reference in `Main` and is released from a shutdown hook during normal shutdown. Crash cleanup is left to the operating system's file-lock release behavior. If the handoff port cannot be reached, the second launch falls back to the Spanish already-running message.

## Files Changed

- `src/main/java/controlador/ApplicationInstanceLock.java`
- `src/main/java/controlador/Main.java`
- `src/test/java/controlador/ApplicationInstanceLockTest.java`
- `docs/pipelines/application-shell-pipeline.md`

## Verification

- `mvn -q -Dtest=ApplicationInstanceLockTest test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/application-multiple-instances.md`

## Regression Notes

Keep the single-instance guard before configuration loading, controller construction, and window creation. Future startup changes should not move `GestorServidores`, `GestorConfiguracion`, or UI initialization before the lock acquisition.

Keep the focus handoff secondary to the file lock so shared state remains protected even if localhost socket setup fails.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
