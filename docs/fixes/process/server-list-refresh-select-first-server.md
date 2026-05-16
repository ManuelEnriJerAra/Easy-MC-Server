# Server List Refresh Select First Server Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-list-refresh-select-first-server.md`

## Scope

Restore the expected right-panel selection after a manual refresh removes the currently selected server because its folder no longer exists. Keep the empty-right-panel behavior only for truly empty server lists.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Reviewed application-shell and server-lifecycle guidance plus the existing stale-server refresh fix note. |
| DONE | 2. Compare refresh and delete flows | Confirmed the refresh path cleared the stale selection but did not choose a fallback server, unlike the explicit delete flow. |
| DONE | 3. Implement selection fallback | Updated the refresh handler to select the first remaining server and only clear the right panel when no servers remain. |
| TO DO | 4. Verify behavior | Manually delete a selected server folder, refresh, and confirm the first remaining server becomes selected. |

## Implementation Notes

The safest minimal fix was to reuse `PanelServidores.seleccionarPrimero()` from the existing EDT refresh path instead of introducing a separate selection algorithm for refreshes.

## Verification Notes

Maven was not available in this shell, so verification remains a manual UI check for now.
