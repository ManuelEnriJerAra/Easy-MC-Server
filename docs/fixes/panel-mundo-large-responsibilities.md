# PanelMundo Large Responsibilities

## Status

Fixed

## Original Issue

`PanelMundo` owned many unrelated world UI responsibilities, including recent connections and debug connection controls, making future world changes riskier.

## Root Cause

Recent connection data lookup, debug fake rows, row rendering, and Debug mode listener management were embedded directly in `PanelMundo` beside preview rendering and world settings logic.

## Solution

Extracted recent-connection behavior into `WorldRecentConnectionsPanel`, a package-private helper that owns the card, debug controls, fake connection state, merge logic, listener lifecycle, and compact row rendering. `PanelMundo` now delegates recent-connection refreshes to that helper.

## Files Changed

- `src/main/java/vista/PanelMundo.java`
- `src/main/java/vista/WorldRecentConnectionsPanel.java`
- `src/test/java/vista/PanelMundoDebugConnectionsTest.java`
- `docs/fixes/process/panel-mundo-large-responsibilities.md`

## Verification

- `mvn -q "-Dtest=GestorServidoresTest,PanelMundoDebugConnectionsTest" test`
- `mvn -q -DskipTests compile`

Both passed. Maven emitted the expected `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/fixes/process/panel-mundo-large-responsibilities.md`

## Regression Notes

Keep preview generation in `PanelMundo` until it can be extracted with dedicated lifecycle tests. Recent-connection behavior should stay in the helper and preserve the compact `PlayerIdentityView` row layout.

## Related Docs

- `docs/pipelines/world-management-pipeline.md`
- `docs/pipelines/world-rendering-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
