# Info Panel Content And Navigation

## Status

Pending

## Area

`vista.VentanaPrincipal` right-side navigation and `PaginaDerecha.INFO` content.

## Feature Request

Make the existing `Información` page a usable real panel instead of leaving it empty and treating the info button primarily as a hidden debug trigger.

## Motivation

The app already defines `PaginaDerecha.INFO` and adds an empty info card to the right-side card layout. Users expect the info button to open that panel normally.

## Desired Behavior

- Clicking `Información` should enter/show the existing info page.
- The page should contain useful application/server information rather than an empty panel.
- Debug-mode toggling should not prevent normal access to the info panel.
- If the debug shortcut remains tied to the info button, it should coexist with normal navigation without making the panel feel inaccessible.

## Notes

- User clarified that the info panel is real, but currently empty.
- `VentanaPrincipal.navegarAPaginaDerecha(...)` special-cases `PaginaDerecha.INFO` and returns without showing the page.
- `renderizarPanelDerecho(...)` currently creates `JPanel info = new JPanel(new BorderLayout())` and adds it under `PaginaDerecha.INFO`.
- Preserve existing Spanish UI terminology: `Información`.

## Suggested Approach

- Replace the empty info panel with a small dedicated builder/component for app and selected-server details.
- Adjust the info navigation branch so it shows `PaginaDerecha.INFO`.
- Move debug toggling to a non-blocking gesture, such as repeated clicks while still navigating, or a modifier-assisted shortcut.

## Verification

- Run `mvn -q -DskipTests compile`.
- Manually open the app, select a server, click `Información`, and confirm the info page is visible.
- Confirm repeated info clicks still toggle Debug mode only if that shortcut is intentionally retained.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
