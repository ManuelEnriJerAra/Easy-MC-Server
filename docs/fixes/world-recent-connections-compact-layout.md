# World Recent Connections Compact Layout

## Status

Fixed

## Original Issue

The "Últimas conexiones" card in the World panel looked inconsistent with compact player/user lists. Rows could show coordinate/location text below the identity, which made the timestamp sit against a taller stack instead of lining up with the compact player identity row.

## Root Cause

`PanelMundo.crearFilaConexion(...)` wrapped `PlayerIdentityView` in a vertical text panel and optionally rendered `RecentConnection.location`. `PanelMundo.renderConexiones(...)` also inserted vertical gaps between rows. Together, those details made recent-connection rows taller and more spaced than compact player rows, and prevented the date/time label from visually centering beside the player identity.

## Solution

`PanelMundo.crearFilaConexion(...)` now renders `PlayerIdentityView.SizePreset.COMPACT` directly as the row identity, keeps the timestamp in the trailing area, centers the timestamp vertically, and no longer renders coordinates/location in the recent-connections card. `PanelMundo.renderConexiones(...)` no longer adds vertical spacing between compact rows.

The `RecentConnection.location` field remains available for existing debug/fallback data flow, but it is not displayed in this card.

## Files Changed

- `src/main/java/vista/PanelMundo.java`
- `docs/pipelines/world-management-pipeline.md`

## Verification

- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/world-recent-connections-compact-layout.md`

## Regression Notes

If recent-connection rows become tall, uneven, or visibly separated again, inspect `PanelMundo.crearFilaConexion(...)` for extra vertical content around `PlayerIdentityView` and `PanelMundo.renderConexiones(...)` for inserted row gaps.

If coordinates reappear in "Últimas conexiones", keep the location data in `RecentConnection` if needed but avoid rendering it in this compact card.

## Related Docs

- `docs/pipelines/world-management-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/debug-mode-pipeline.md`
