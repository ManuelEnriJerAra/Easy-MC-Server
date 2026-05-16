# PanelMundo Large Responsibilities Process

## Status

Fixed

## Linked Fix

- `docs/fixes/panel-mundo-large-responsibilities.md`

## Scope

Reduce `PanelMundo` responsibility density with a behavior-preserving extraction around the recent-connections card. Preview generation and world settings remain in `PanelMundo`.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked world management, world rendering, and UI component pipeline docs. |
| DONE | 2. Choose extraction target | Selected "Ultimas conexiones" because it is bounded and already has focused debug-merge tests. |
| DONE | 3. Extract helper | Added `WorldRecentConnectionsPanel` for recent connection rendering, debug controls, fake debug rows, and merge logic. |
| DONE | 4. Verify behavior | Updated the debug-connection test to target the helper; targeted tests and compile passed. |

## Implementation Notes

The helper receives suppliers for the current selected server and world. It owns its Debug mode listener and removes it when the card becomes undisplayable. Compact player rows still use `PlayerIdentityView.SizePreset.COMPACT`.

## Verification Notes

- `mvn -q "-Dtest=GestorServidoresTest,PanelMundoDebugConnectionsTest" test` passed.
- `mvn -q -DskipTests compile` passed.
