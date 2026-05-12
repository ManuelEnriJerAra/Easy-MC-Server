# Debug Recent Connections Controls Process

## Status

Completed

## Linked Fix

- `docs/fixes/debug-recent-connections-controls.md`

## Scope

Retrospective process note for the completed debug recent-connections controls fix. This file was created when `docs/fixes/process/` became the standard location for detailed fix process records, so it summarizes the known completed work from the solved-fix note rather than an active in-progress checklist.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Identify debug testing gap | `PanelMundo` could only render recent connections from logs or playerdata fallback, leaving no debug-only way to exercise empty, partial, or full recent-connection states. |
| DONE | 2. Add debug-only controls | Added plus/minus controls in the recent-connections card header that are visible only while `DebugMode.isEnabled()` is true. |
| DONE | 3. Keep fake state isolated | Stored fake recent connections in memory, merged them ahead of real entries only while Debug mode is enabled, and preserved the existing displayed-entry limit. |
| DONE | 4. Clean up on mode changes | Cleared fake recent connections when Debug mode is disabled and registered live visibility behavior through `DebugMode.PROPERTY_ENABLED`. |
| DONE | 5. Prevent stale listeners | Removed the Debug mode listener when the panel becomes undisplayable so the listener cannot outlive the component. |
| DONE | 6. Verify compile | Ran `mvn -q -DskipTests compile` after the implementation. |

## Implementation Notes

The important guardrail is that debug-only recent connections never mutate real server logs, playerdata, or persisted configuration. If similar debug controls are added elsewhere, keep fake state in memory and clear it when Debug mode turns off.

## Verification Notes

- `mvn -q -DskipTests compile`
