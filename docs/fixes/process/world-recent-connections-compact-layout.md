# World Recent Connections Compact Layout Process

## Status

Completed

## Linked Fix

- `docs/fixes/world-recent-connections-compact-layout.md`

## Scope

Complete the pending fix for the World panel recent-connections card. The fix is limited to row presentation: keep existing data sources, debug fake connections, and stored location data intact, but render rows with the compact player identity style and hide coordinates/location from this card.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Read repository instructions plus world, UI component, and Debug mode pipeline docs. |
| DONE | 2. Implement compact row layout | Refactored `PanelMundo.crearFilaConexion(...)` so rows use `PlayerIdentityView.SizePreset.COMPACT` directly, center the timestamp, and render without vertical gaps between compact users. |
| DONE | 3. Verify compile | Ran the required Maven compile check for the UI/layout fix. |
| DONE | 4. Move fix documentation | Added the solved fix note, updated the pipeline note, and deleted the pending-fix note. |

## Implementation Notes

The `RecentConnection.location` value may still be produced by debug fake rows and playerdata fallback, but this card does not render that field. Keeping the record unchanged avoids changing data contracts used by preview overlays or future diagnostics.

The row height now comes from the larger of the compact identity view and timestamp preferred heights, so `BoxLayout` in `conexionesPanel` can keep rows stable without the old two-line wrapper.

`PanelMundo.renderConexiones(...)` intentionally adds each compact recent-connection row directly, without `Box.createVerticalStrut(...)`, matching the standardized compact user list spacing.

## Verification Notes

- `mvn -q -DskipTests compile`
