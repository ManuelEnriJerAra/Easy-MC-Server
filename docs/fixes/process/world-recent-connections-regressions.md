# World Recent Connections Regressions Process

## Status

Fixed

## Linked Fix

- `docs/fixes/world-recent-connections-regressions.md`

## Scope

Fix the extracted recent-connections helper so it renders Spanish copy correctly, avoids repeated users, applies the same four-row cap consistently to debug and real data, and keeps the compact rows visually packed without leftover vertical slack.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Reviewed world-management, debug-mode, and UI guidance plus the recent-connections extraction notes. |
| DONE | 2. Audit the extracted helper | Found the mojibaked title string and confirmed duplicate users could survive both log parsing and merged rendering. |
| DONE | 3. Implement bounded dedupe flow | Added shared recent-connection dedupe/cap helpers, fixed the card title copy, and kept debug rows bounded to the same limit. |
| DONE | 4. Align compact row layout | Switched the rendered rows to a top-anchored `GridBagLayout` container with a filler row, matching the compact player-list geometry more closely. |
| DONE | 5. Add regression coverage | Extended `PanelMundoDebugConnectionsTest` for repeated users and the four-entry cap with real log lines. |
| TO DO | 6. Verify in app | Open the World panel and confirm the title, dedupe behavior, four-row limit, and compact row spacing visually. |

## Implementation Notes

The helper now treats the four-entry limit as a rendering contract. Log parsing stops after four unique usernames, and the final merge step removes repeated usernames again so debug rows and real rows cannot render duplicates together.

The row-gap follow-up showed that removing explicit `Box.createVerticalStrut(...)` calls was not enough on its own. The rows also need a container that anchors them at the top instead of letting the card's center area visually exaggerate spare height.

## Verification Notes

Maven was not available in this shell, so the added tests could not be executed here. Manual World-panel verification is still needed.
