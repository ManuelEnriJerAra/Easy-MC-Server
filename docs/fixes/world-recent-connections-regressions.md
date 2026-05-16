# World Recent Connections Regressions

## Status

Fixed

## Original Issue

The extracted recent-connections helper introduced a mojibaked card title, could show repeated users, did not make the four-entry cap obvious for real recent-connection data, and still left visible vertical slack in the compact rows area.

## Root Cause

The new helper contained one incorrectly encoded UI literal, preserved duplicate joins from log parsing, relied on per-source limits instead of a single final dedupe-and-cap pass for the rendered list, and mounted the rows panel directly into a stretching content slot instead of pinning compact rows to the top like the player list does.

## Solution

`WorldRecentConnectionsPanel` now uses correct UTF-8 copy for "Últimas conexiones", deduplicates recent connections by username while keeping the newest entry first, enforces the four-row limit after debug/real merging as well as during log parsing, and renders rows inside a top-anchored `GridBagLayout` container with a filler row so compact entries do not appear separated by leftover vertical space.

## Files Changed

- `src/main/java/vista/WorldRecentConnectionsPanel.java`
- `src/test/java/vista/PanelMundoDebugConnectionsTest.java`
- `docs/pipelines/world-management-pipeline.md`
- `docs/fixes/process/world-recent-connections-regressions.md`

## Verification

- `PanelMundoDebugConnectionsTest` covers duplicate-user removal across debug and real data.
- `PanelMundoDebugConnectionsTest` covers the four-user cap for parsed real log joins.
- Manual check: open Mundo and confirm the card title renders as "Últimas conexiones" and the compact rows stay visually packed at the top without extra vertical gaps.

## Detailed Process

- `docs/fixes/process/world-recent-connections-regressions.md`

## Regression Notes

Keep the recent-connections limit as a final rendered-list rule, not only a data-source rule. If users repeat, inspect both log parsing and debug/real merge logic for dedupe drift.

If compact rows look visually separated again, compare the helper container layout with `PanelJugadores` and keep the rows pinned to the top with an explicit filler instead of relying on a stretching center slot.

## Related Docs

- `docs/pipelines/world-management-pipeline.md`
- `docs/pipelines/debug-mode-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
