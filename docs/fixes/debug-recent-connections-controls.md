# Debug Recent Connections Controls

## Status

Fixed

## Original Issue

Debug mode needed plus/minus controls in "Ultimas conexiones" so recent-connection behavior could be tested without relying on real logs or playerdata.

## Root Cause

`PanelMundo` rendered recent connections only from server logs or playerdata fallback. There was no debug-only in-memory path to exercise empty/partial/full list behavior.

## Solution

`PanelMundo` now:

- adds debug plus/minus buttons to the recent-connections card header
- stores fake recent connections in memory
- merges fake connections ahead of real entries while Debug mode is enabled
- caps the displayed list to the same recent-connection limit
- clears fake state when Debug mode is disabled
- unregisters the Debug mode listener when undisplayable

## Files Changed

- `src/main/java/vista/PanelMundo.java`

## Verification

- `mvn -q -DskipTests compile`

## Regression Notes

If fake connections persist after disabling Debug mode, inspect `actualizarModoDebugConexiones()`.

If controls do not appear/disappear live, inspect the `DebugMode.PROPERTY_ENABLED` listener and card header action panel wiring.

## Related Docs

- `docs/pipelines/debug-mode-pipeline.md`
- `docs/pipelines/world-management-pipeline.md`
