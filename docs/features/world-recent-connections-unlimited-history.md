# World Recent Connections Unlimited History

## Status

Implemented

## Feature

The World panel recent-connections card now shows the full recent history instead of stopping at four entries.

## Motivation

The recent-connections data already comes from server logs or playerdata files without a natural product limit, and the four-row cap prevented checking larger histories in both real usage and Debug mode.

## Solution

`WorldRecentConnectionsPanel` no longer truncates recent connections to four rows. It still deduplicates repeated users by username while keeping the newest occurrence first, and it now renders the list inside a scroll pane so long histories stay usable.

`WorldPlayerDataService.findRecentPlayers(...)` also now treats non-positive limits as unlimited, which keeps the fallback path aligned with the UI behavior.

## Files Changed

- `src/main/java/vista/WorldRecentConnectionsPanel.java`
- `src/main/java/controlador/world/WorldPlayerDataService.java`
- `src/test/java/vista/PanelMundoDebugConnectionsTest.java`
- `docs/pipelines/world-management-pipeline.md`
- `docs/features/process/world-recent-connections-unlimited-history.md`

## Verification

- Targeted recent-connections tests were updated for unlimited-history behavior.
- Manual check: spam the Debug `+` button and confirm more than four rows can be added and browsed.

## Detailed Process

- `docs/features/process/world-recent-connections-unlimited-history.md`

## Follow-Up Notes

If the list grows very large in real servers, the next expansion point is virtualization or a user-facing history window. For now, a scrollable compact card keeps the feature simple and usable.

## Related Docs

- `docs/pipelines/world-management-pipeline.md`
- `docs/features/world-recent-connections-unlimited-history.md`
