# World Recent Connections Layout Looks Inconsistent

## Status

Pending

## Area

`PanelMundo` recent connections card and compact player/user list styling.

## Issue

The "Últimas conexiones" section in the World panel has an inconsistent visual layout compared with the standardized compact user/player list look used elsewhere. Recent connection rows show extra coordinate/location content and the date/time text is not vertically centered with the user identity panel, making the card look unpolished and harder to scan.

## Desired Behavior

Recent connection rows should use the standardized compact user-list appearance:

- Use `PlayerIdentityView` or the same compact visual structure as other player/user rows.
- Vertically center the date and time with the user identity panel.
- Do not show player coordinates/location in this card.
- Keep row spacing, height, borders, and typography consistent with compact player list rows.
- Preserve Spanish UI copy such as `Últimas conexiones`.

## Evidence

- `PanelMundo.crearCardConexiones()` builds the recent connections card.
- `PanelMundo.renderConexiones(...)` renders the row list.
- `PanelMundo.crearFilaConexion(...)` currently creates each row and includes `ubicacion`/location text.
- The current row layout separates identity, date, and location in a way that does not match the compact user list components.
- `PanelJugadores` and `PlayerIdentityView.SizePreset.COMPACT` provide the visual precedent for compact player/user rows.

## Suggested Approach

Refactor only the recent-connections row rendering:

- Reuse `PlayerIdentityView` with `SizePreset.COMPACT`.
- Remove coordinate/location label rendering from the visible row.
- Place the timestamp label in the row's trailing area and align it vertically center.
- Keep debug fake connections working, but ignore their fake location in the UI.
- If needed, keep location data in the `RecentConnection` record for future internal use, but do not render it.
- Verify the row height remains stable in both real and debug connection lists.

## Verification

- Open the World panel and confirm "Últimas conexiones" rows match the compact user-list style.
- Confirm date/time text is vertically centered beside the user identity.
- Confirm coordinates are not shown.
- Enable Debug mode, add fake recent connections, and confirm the same layout applies.
- Run:

```bash
mvn -q -DskipTests compile
```

## Related Docs

- `docs/pipelines/world-management-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/debug-mode-pipeline.md`
