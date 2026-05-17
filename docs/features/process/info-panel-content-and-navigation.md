# Info Panel Content And Navigation Process

## Status

Implemented

## Linked Feature

- `docs/features/info-panel-content-and-navigation.md`

## Scope

Implement the existing `Información` navigation page as a real about/support panel. The panel should reuse Dora's current Swing/FlatLaf visual language, existing logo and SVG assets, existing social links, and the requested support email `manu.ejerara@gmail.com`. In this page only, the normal server list column should be hidden so the panel can use the freed space.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md`, `application-shell-pipeline.md`, and `ui-component-pipeline.md` before editing the main shell and shared UI patterns. |
| DONE | 2. Locate reusable content | Reused the social/support links from `NoServerFrame` and existing logo/icon assets under `src/main/resources`. |
| DONE | 3. Implement the panel | Added a dedicated info panel, normal `INFO` page wiring, shared link constants, and a server-list collapse while the info page is active. |
| DONE | 4. Verify behavior | Ran compile and inspected the relevant diffs. Manual UI verification should still open `Información`, confirm the panel appears, and confirm other pages restore the server list. |

## Implementation Notes

`Información` is now treated as a normal right-side navigation page instead of being special-cased away from the card layout. The hidden debug click sequence remains in `VentanaPrincipal`, but it no longer prevents normal navigation.

The server list is hidden by collapsing the main split pane's left wrapper only while `PaginaDerecha.INFO` is active. Leaving the info page restores the left wrapper, divider size, and previous divider location when available.

The panel uses `CardPanel`, `BoxCategory`, `AppTheme`, and `SvgIconFactory`, with the existing `/doraapp/logo.png`, `/doraapp/dora-real.jpeg`, and existing `github`, `linkedin`, `ko-fi`, and `chat` SVGs.

## Verification Notes

`mvn -q -DskipTests compile` passed with only the expected Lombok `sun.misc.Unsafe` warning.

Manual UI verification was not run in this session.
