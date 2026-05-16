# Server List Refresh Stale Deleted Folders Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-list-refresh-stale-deleted-folders.md`

## Scope

Make the manual server-list refresh reconcile saved entries with the filesystem, remove stale app entries only, clear stale selection state, and persist the cleaned list.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked server lifecycle and application shell docs plus existing startup cleanup logic. |
| DONE | 2. Add manager refresh | Added `GestorServidores.refrescarServidoresGuardados()` reusing persisted-server validation and clearing removed selection. |
| DONE | 3. Wire UI refresh | The refresh button now calls the manager reconcile path, shows the existing warning, redraws the list, and clears stale right-panel state when needed. |
| DONE | 4. Verify behavior | Added `GestorServidoresTest` coverage; targeted tests and compile passed. |

## Implementation Notes

The refresh path does not delete files. It removes app entries whose server directories are no longer cargable, then saves the JSON through the existing persistence path so stale entries do not return on restart.

## Verification Notes

- `mvn -q "-Dtest=GestorServidoresTest,PanelMundoDebugConnectionsTest" test` passed.
- `mvn -q -DskipTests compile` passed.
