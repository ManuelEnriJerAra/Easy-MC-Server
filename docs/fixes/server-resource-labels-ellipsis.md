# Server Resource Labels Ellipsis

## Status

Fixed

## Original Issue

The compact CPU, RAM, and DISCO resource indicators could show their metric labels or values with ellipses. These strings are core server status information and should remain fully visible.

## Root Cause

The left server column could be narrowed below the width needed by the fixed three-card resource strip. Each indicator card then became too small to fit the label, percentage value, gap, and card insets, so Swing clipped the labels during painting.

## Solution

The resource indicator panel stays as a single-row three-card strip. Each metric card reserves stable label space based on the full metric label and a worst-case `100%` value. The main shell opens the left column at the readable single-row width, while keeping a smaller minimum so users can still resize the pane.

## Files Changed

- `src/main/java/vista/PanelIndicadoresRecursos.java`
- `src/main/java/vista/VentanaPrincipal.java`
- `src/test/java/vista/PanelIndicadoresRecursosTest.java`

## Verification

- `mvn -q "-Dtest=vista.PanelIndicadoresRecursosTest" test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/server-resource-labels-ellipsis.md`

## Regression Notes

If these labels ellipsize at the normal/default column width, inspect `PanelIndicadoresRecursos.READABLE_SINGLE_ROW_WIDTH` and the card label sizing. The resource strip should remain one row; do not wrap CPU/RAM/DISCO cards to solve clipping. The split pane minimum is intentionally smaller for user resizing.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/panel-map.md`
