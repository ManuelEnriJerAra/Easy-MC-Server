# Duplicate Text Ellipsis Helpers

## Status

Pending

## Area

Swing UI text rendering helpers.

## Issue

Several UI classes implement their own ellipsis logic independently. This increases the chance of inconsistent text clipping, font rendering differences, and repeated bug fixes.

## Desired Behavior

Common ellipsis behavior should live in a shared UI helper where practical, with explicit support for right ellipsis and left ellipsis. Components should reuse that helper instead of copying binary-search/string-width code.

## Evidence

- `PanelServidores.ellipsizePx(...)`
- `PanelControlServidor.ellipsizePx(...)`
- `PanelExtensiones.ellipsize(...)`
- `ExtensionMarketplaceDialog.ellipsize(...)`
- `GestorServidores.LeftEllipsisLabel`

## Suggested Approach

Create a small helper in `vista`, for example `TextEllipsizer`, with methods for:

- right ellipsis
- left ellipsis
- available width handling

Adopt it gradually in touched files to avoid a large risky UI churn.

## Verification

- `mvn -q -DskipTests compile`
- Manual checks on server list, control buttons, extension rows, marketplace rows, and create-server folder editor.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/panel-map.md`
