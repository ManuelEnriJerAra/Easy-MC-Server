# Duplicate Text Ellipsis Helpers Fix

## Status

Fixed

## Original Issue

Several Swing UI classes implemented their own pixel-width ellipsis logic. The repeated helpers made text clipping behavior harder to keep consistent across server rows, control buttons, extension rows, marketplace rows, preview labels, and the create-server folder path editor.

## Root Cause

The codebase had no shared text ellipsis helper. Each component copied string-width and binary-search logic locally, with small policy differences for whether to return `...`, `.`, or an empty string when the available width is tiny.

## Solution

Added `vista.TextEllipsizer` with shared right ellipsis, strict right ellipsis, and left ellipsis methods. Migrated practical duplicated call sites to use it while preserving the existing tiny-width behavior of each UI area.

## Files Changed

- `src/main/java/vista/TextEllipsizer.java`
- `src/main/java/vista/PanelServidores.java`
- `src/main/java/vista/PanelPrevisualizacion.java`
- `src/main/java/vista/PanelControlServidor.java`
- `src/main/java/vista/PanelExtensiones.java`
- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/test/java/vista/TextEllipsizerTest.java`
- `docs/pipelines/ui-component-pipeline.md`

## Verification

- `mvn -q "-Dtest=vista.TextEllipsizerTest" test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/ui-duplicate-ellipsis-helpers.md`

## Regression Notes

Use `TextEllipsizer` when clipping text by pixel width. Use `right(...)` for normal label/button truncation, `rightStrict(...)` for rows where the returned text must fit even when only a single dot is available, and `left(...)` for path-like text where the suffix must remain visible.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/panel-map.md`
