# World Recent Connections Unlimited History Process

## Status

Completed

## Linked Feature

- `docs/features/world-recent-connections-unlimited-history.md`

## Scope

Remove the hard four-entry limit from the World panel recent-connections card. Keep the existing data-source order and duplicate-user filtering, but allow the UI to show the full recent history with scrolling instead of truncating the list.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Reviewed the world-management pipeline plus feature documentation requirements. |
| DONE | 2. Remove the artificial cap | Updated the recent-connections helper and playerdata service so they no longer stop at four entries. |
| DONE | 3. Keep the UI usable | Added a scroll pane around the recent-connections rows so long histories do not stretch the World panel uncontrollably. |
| DONE | 4. Update tests and docs | Replaced four-entry expectations with unlimited-history coverage and documented the new behavior. |
| TO DO | 5. Verify behavior | Run targeted checks or note any environment blockers. |

## Implementation Notes

The data already comes from files with no meaningful product-level four-row limit: recent join lines come from `Server.getRawLogLines()` and the fallback reads all `playerdata/*.dat` files before sorting by last-seen time.

The UI now keeps the compact row layout but renders it inside a scroll pane so an unlimited history remains usable inside the existing card.

## Verification Notes

Verification has not run yet in this shell.
