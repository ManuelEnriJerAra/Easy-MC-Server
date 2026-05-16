# Server List Scroll Reset On Start

## Status

Fixed

## Original Issue

Starting a server from the server-list right-click menu could rebuild the server list and move the list scroll position back to the top.

## Root Cause

The start flow saves the server after synchronizing startup settings, which fires the `listaServidores` change event and rebuilds `PanelServidores`. The panel tried to restore the previous viewport position immediately after replacing the view, but the new view could still report an unset size, clamping the restored Y position to `0`.

## Solution

`PanelServidores` now restores the previous viewport position using the new view preferred size and schedules a second restore after Swing lays out the rebuilt list.

## Files Changed

- `src/main/java/vista/PanelServidores.java`
- `src/test/java/vista/PanelServidoresTest.java`

## Verification

- `mvn -q -Dtest=PanelServidoresTest test`
- `mvn -q -DskipTests compile`

Both passed. Maven emitted the expected Lombok `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/fixes/process/server-list-scroll-reset-on-start.md`

## Regression Notes

When replacing a scrollpane viewport view, do not clamp against `getWidth()` / `getHeight()` alone before layout has run. Use preferred size as a fallback and defer scroll restoration with `SwingUtilities.invokeLater(...)` when the view was just rebuilt.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
