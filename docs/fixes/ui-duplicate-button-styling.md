# Duplicate Button Styling Fix

## Status

Fixed

## Original Issue

Several Swing panels configured action buttons, header icon buttons, debug controls, and hover states with local helper methods. The duplicated styling made those buttons easier to drift visually and behaviorally.

## Root Cause

`AppTheme` had base button style methods, but it did not expose reusable helpers for the common configured button shapes used by panels. Panels filled the gap by repeating tooltip, icon, sizing, cursor, border, background, and hover setup.

## Solution

Added shared `AppTheme` helpers for configured header icon buttons, debug icon buttons, fixed-size row action icon buttons, and large server control buttons. Migrated the targeted panel helpers and direct style calls in players, worlds, extensions, config, and server controls to use those shared helpers.

## Files Changed

- `src/main/java/vista/AppTheme.java`
- `src/main/java/vista/PanelJugadores.java`
- `src/main/java/vista/PanelMundo.java`
- `src/main/java/vista/PanelExtensiones.java`
- `src/main/java/vista/PanelConfigServidor.java`
- `src/main/java/vista/PanelControlServidor.java`
- `docs/pipelines/ui-component-pipeline.md`

## Verification

- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/ui-duplicate-button-styling.md`

## Regression Notes

When adding icon-only header, debug, row action, or server control buttons, use `AppTheme` helpers so panels only choose icon, tooltip, and behavior. Selection-specific buttons such as navigation controls may still need local state handling, but should continue to reuse `AppTheme` colors and borders.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/debug-mode-pipeline.md`
