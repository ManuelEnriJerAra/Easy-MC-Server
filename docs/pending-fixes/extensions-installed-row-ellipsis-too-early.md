# Installed Extension Rows Ellipsize Text Too Early

## Status

Pending

## Area

Installed mods/plugins list rendering and shared text ellipsis behavior.

## Issue

Installed extension rows ellipsize names, labels, authors, and descriptions too early. In the installed mods/plugins list, visible rows have enough horizontal room for more text, but metadata still truncates after a short prefix. This makes descriptions and labels harder to scan and hides useful marketplace/local metadata.

## Desired Behavior

Installed extension rows should use the available row width more accurately before applying ellipsis. Names, type/source labels, authors, and descriptions should only truncate when they truly exceed the available text region, and description text should preserve as much useful content as possible.

## Evidence

- Screenshot shows installed plugin rows where names like `FancyHologra...` and `MiniPlacehold...` truncate while there is still row space.
- Secondary labels such as `Plugin / by Autor de...` and descriptions such as `Simple, lightweight and fast hologram p...` truncate very early.
- Existing shared `TextEllipsizer` helpers are centralized, but installed extension row sizing may still be using conservative widths or local calculations that do not match the actual rendered bounds.

## Suggested Approach

Relate this fix to the ellipsis centralization work:

- Inspect installed extension row rendering in `PanelExtensiones` and any shared marketplace/detail row helpers.
- Replace ad hoc or overly conservative width calculations with shared `TextEllipsizer` usage fed by the actual available component width.
- If row layout math is non-trivial, extract a small helper similar to `FolderPathLayout` so width allocation can be tested headlessly.
- Check whether icon width, row padding, status badges, action buttons, or compact/full view modes are being subtracted twice.
- Add focused tests for row text width allocation and ellipsis thresholds where possible.
- Preserve Spanish UI terminology and existing compact/full row design.

## Verification

- Installed extension rows display noticeably more metadata before ellipsizing.
- Long names/descriptions still truncate cleanly without overlapping icons, status labels, or action buttons.
- Compact and expanded/list modes still render correctly.
- Run `mvn -q -DskipTests compile`.
- Run targeted UI helper tests if a layout/ellipsis helper is extracted or updated.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/fixes/tests-ui-layout-coverage.md`
