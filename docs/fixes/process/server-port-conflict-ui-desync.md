# Server Port Conflict UI Desync Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-port-conflict-ui-desync.md`

## Scope

Keep the startup port-conflict rewrite synchronized with persisted model state and the active configuration panel, without silently discarding unrelated unsaved editor changes.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked server lifecycle, configuration, and application shell pipeline docs. |
| DONE | 2. Implement notification path | `GestorServidores.iniciarServidor(...)` now fires `configuracionServidor` after writing the selected startup port and saving the server. |
| DONE | 3. Preserve dirty config edits | `PanelConfigServidor` listens for selected-server config events; it reloads when clean and only applies the external `server-port` field when dirty. |
| DONE | 4. Verify behavior | Targeted tests and compile passed. |

## Implementation Notes

The config panel listener is registered while the panel is displayable and removed when it is undisplayable so old panels do not retain manager listeners. When the form is dirty, only `server-port` is reconciled against the new persisted value; if the user has already edited that field, their edit stays visible and the save button remains dirty.

## Verification Notes

- `mvn -q "-Dtest=GestorServidoresTest,PanelMundoDebugConnectionsTest" test` passed.
- `mvn -q -DskipTests compile` passed.
