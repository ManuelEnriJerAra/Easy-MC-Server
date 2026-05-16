# Server Resource Labels Ellipsis Process

## Status

Completed

## Linked Fix

- `docs/fixes/server-resource-labels-ellipsis.md`

## Scope

Fix the compact CPU, RAM, and DISCO resource indicators so their labels and percentage values do not ellipsize in the left server column. Keep the change scoped to the resource indicator strip and avoid changing unrelated row ellipsis behavior.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md`, `docs/pipelines/ui-component-pipeline.md`, and `docs/pipelines/panel-map.md`; resource indicators map to `PanelIndicadoresRecursos`. |
| DONE | 2. Locate the squeeze point | Found the indicators need a fixed one-row layout, but the left column could shrink below the width needed for three readable cards. |
| DONE | 3. Implement resilient layout | Kept the fixed single-row grid, gave each metric card measured label sizing, and kept the default left-column width at the indicator strip's readable width while preserving a smaller resize minimum. |
| DONE | 4. Add regression coverage | Added a Swing layout test that lays out the indicator panel at the required left-column width and verifies the labels stay readable in one row. |
| DONE | 5. Verify behavior | Ran the focused test and the project compile command. |

## Implementation Notes

The label text itself was not pre-truncated through `TextEllipsizer`; the visible ellipsis came from Swing painting labels inside cells that were too narrow. With three equal cells below the required left-column width, each card had less content width than `DISCO 100%` requires after card insets and label gaps.

`PanelIndicadoresRecursos` now stays on the fixed three-column grid and exposes `READABLE_SINGLE_ROW_WIDTH` for the shell's default left-column width. `VentanaPrincipal` keeps a smaller split-pane minimum so users can still resize the left pane. Each `MetricIndicatorCard` sets stable label and card dimensions from actual font metrics so `CPU`, `RAM`, `DISCO`, and values up to `100%` have reserved space at the default readable width.

## Verification Notes

- `mvn -q "-Dtest=vista.PanelIndicadoresRecursosTest" test`
- `mvn -q -DskipTests compile`
- Maven emitted the expected `sun.misc.Unsafe` warning from Lombok-related startup output.
