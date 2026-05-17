# No Server Welcome Refresh Process

## Status

Implemented

## Linked Feature

- `docs/features/no-server-welcome-refresh.md`

## Scope

Modernize the welcome frame shown when Dora has no managed servers. The refresh keeps the create/import flow intact, adds project and personal links requested by the user, and leaves room for richer project information to move into the information pane later.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked the application shell and UI component pipelines before changing the startup surface. |
| DONE | 2. Implement the frame refresh | Reworked `NoServerFrame` around the existing theme helpers, SVG icon assets, and server creation/import actions. |
| DONE | 3. Verify behavior | Compiled the project and inspected the changed files. |

## Implementation Notes

The no-server frame is a standalone startup surface rather than a server-specific right-side panel. It should keep the same window close behavior and only change the presentation around the existing `GestorServidores.crearServidor()` and `GestorServidores.importarServidor()` paths.

## Verification Notes

Verification performed:

- `mvn -q -DskipTests compile`
