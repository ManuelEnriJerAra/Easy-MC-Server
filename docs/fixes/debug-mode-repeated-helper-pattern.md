# Repeated Debug Mode Helper Pattern

## Status

Fixed

## Original Issue

Panels with debug-only controls repeated the same listener and header-action wiring, which made cleanup and visibility behavior easy to drift between screens.

## Root Cause

`PanelJugadores` and the world recent-connections UI each hand-built the same `DebugMode.PROPERTY_ENABLED` listener flow and header action rebuilding instead of sharing a small Swing-safe helper.

## Solution

Added `vista.DebugModeUiBinder` as the shared helper for debug-mode UI wiring. It now centralizes the filtered EDT listener creation and the header action rebuild pattern, while each panel keeps ownership of its own fake state and clear-on-disable behavior.

## Files Changed

- `src/main/java/vista/DebugModeUiBinder.java`
- `src/main/java/vista/PanelJugadores.java`
- `src/main/java/vista/WorldRecentConnectionsPanel.java`
- `docs/pipelines/debug-mode-pipeline.md`

## Verification

- `mvn -q -DskipTests compile` not run here because `mvn` is unavailable in this shell.
- Manual follow-up: toggle Debug mode and verify both player and recent-connection debug controls appear/disappear and clear fake state when disabled.

## Detailed Process

- `docs/fixes/process/debug-mode-repeated-helper-pattern.md`

## Regression Notes

New debug-only header controls should use `DebugModeUiBinder` for listener registration and action-row rebuilding instead of duplicating the pattern inline.

## Related Docs

- `docs/pipelines/debug-mode-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
