# Application Allows Multiple Instances

## Status

Pending

## Area

Application startup and shared server/config state.

## Issue

The app can currently be launched multiple times at once. Separate instances may read and write the same managed server list, server configuration, logs, process state, and UI-derived caches independently, which can cause desync, lost updates, duplicated actions, or unsafe server lifecycle behavior.

## Desired Behavior

Only one Easy MC Server instance should run per user profile/config scope. A second launch should detect the existing instance, inform the user that the app is already running, and exit without initializing controllers or mutating shared state.

## Evidence

- `controlador.Main` starts the Swing application without a single-instance guard.
- `GestorServidores` persists managed server state and coordinates server lifecycle actions that should not be controlled by two app windows at the same time.
- Shared files such as managed server metadata and server folders can be touched by multiple JVM processes if the user launches the app repeatedly.

## Suggested Approach

Add a startup guard before creating the main window:

- Acquire an OS-level `FileLock` on a lock file in the app config/cache directory.
- Keep the lock channel open for the lifetime of the application.
- If the lock cannot be acquired, show a small Spanish message such as `Easy MC Server ya está en ejecución.` and exit.
- Ensure the lock is released on normal shutdown, while still relying on OS cleanup after crashes.
- Optionally later add a localhost socket handoff so a second launch can ask the first instance to focus its window.

## Verification

- Launch one instance and confirm it starts normally.
- Launch a second instance and confirm it exits with the already-running message.
- Confirm normal shutdown releases the lock and a later launch succeeds.
- Run `mvn -q -DskipTests compile`.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
