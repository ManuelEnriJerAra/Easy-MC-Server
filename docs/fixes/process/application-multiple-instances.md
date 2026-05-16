# Application Multiple Instances Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/application-multiple-instances.md`

## Scope

Resolve the pending issue where multiple Dora JVMs could start against the same user profile/config scope. This covers adding a startup guard before controller/window initialization, keeping the OS file lock alive for the application lifetime, asking the already-running instance to restore/focus itself on duplicate launch, and documenting the solved behavior.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked repository instructions plus the application shell and filesystem/path pipelines. |
| DONE | 2. Inspect startup and app paths | Confirmed `controlador.Main` initialized UI/controllers directly and `AppPaths.locksDirectory()` already defines the per-user lock location. |
| DONE | 3. Implement the guard | Added `ApplicationInstanceLock` and wired `Main` to acquire it before Swing/controller initialization. |
| DONE | 4. Add focus handoff | Added a localhost handoff listener so duplicate launches restore the already-running window before exiting. |
| DONE | 5. Verify behavior | Added unit tests for lock contention/release and focus handoff, then ran compile plus the targeted lock test. |
| DONE | 6. Move documentation | Replaced the pending-fix note with solved fix and process documentation. |

## Implementation Notes

The guard uses `FileChannel.tryLock()` on `AppPaths.locksDirectory().resolve("dora.lock")`. A second JVM receives `null` from `tryLock`; a second lock attempt inside the same JVM is treated the same way by catching `OverlappingFileLockException`.

`Main` acquires the lock before `SwingUtilities.invokeLater(...)`, before theme/config loading, and before creating `GestorServidores`. If the lock is unavailable, the duplicate launch sends `FOCUS` to the first instance's localhost handoff port and returns. The first instance restores either `VentanaPrincipal` or `NoServerFrame`, including when the main frame was hidden in the system tray. If the handoff fails, the duplicate launch shows the Spanish already-running fallback message.

A shutdown hook releases the channel/lock and closes the handoff server during normal shutdown, while the OS still releases the file lock if the process exits unexpectedly.

## Verification Notes

- `mvn -q -Dtest=ApplicationInstanceLockTest test`
- `mvn -q -DskipTests compile`
