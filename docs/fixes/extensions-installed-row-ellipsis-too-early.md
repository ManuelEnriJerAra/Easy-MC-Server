# Installed Extension Row Ellipsis

## Status

Fixed

## Original Issue

Installed extension rows ellipsized names, labels, authors, and descriptions too early. Rows visibly had horizontal space left, but names such as `FancyHolograms` and secondary metadata still truncated after a short prefix.

## Root Cause

`PanelExtensiones.ExtensionCellRenderer` calculated text budgets before Swing laid out the row. It also always reserved a trailing east component, showing either a status SVG or the hover delete SVG, so the text never got the full row width in the normal non-hover state. The server list favorite button does not behave this way: it becomes invisible when not needed, so the text area reclaims that space.

The row also split the title budget evenly between the name and metadata labels. If one label was short, its unused half could not be used by the other label. Descriptions had a fixed 110-character cap before the shared pixel-width ellipsis helper ran.

## Solution

Installed extension rows now use full text labels and a small layout-driven `ExtensionTitleRow` that allocates name and metadata widths from the actual laid-out row width. The labels keep their full text and let Swing clip/ellipsis inside their assigned bounds.

The hover delete SVG is no longer a permanent east-side layout participant. It is added only while the row is hovered, so normal rows use the full available text space and hovered rows shrink by the actual delete icon slot, matching the server-list favorite behavior.

Descriptions now preserve the full single-line metadata text until the label is painted within its actual bounds.

## Files Changed

- `src/main/java/vista/PanelExtensiones.java`
- `src/main/java/vista/InstalledExtensionRowTextLayout.java`
- `src/test/java/vista/InstalledExtensionRowTextLayoutTest.java`

## Verification

- `mvn -q -Dtest=InstalledExtensionRowTextLayoutTest test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/extensions-installed-row-ellipsis-too-early.md`

## Regression Notes

If installed rows truncate too early again, check for fixed fractional budgets such as `titleBudget / 2`, permanent trailing SVG/action slots, stale `list.getWidth()` fallback behavior, or pre-truncation before layout. The delete action should affect text width only when it is visible.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
