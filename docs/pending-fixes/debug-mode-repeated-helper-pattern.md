# Repeated Debug Mode Helper Pattern

## Status

Pending

## Area

`vista.DebugMode` consumers.

## Issue

Panels that add debug controls repeat similar logic: create plus/minus buttons, listen to `DebugMode.PROPERTY_ENABLED`, show/hide controls, clear fake state, and remove listeners. Repetition increases the chance of one panel missing cleanup or behaving differently.

## Desired Behavior

Debug control wiring should use a shared helper or well-documented pattern so panels expose fake state consistently and clean up listeners reliably.

## Evidence

- `PanelJugadores`
- `PanelMundo`

## Suggested Approach

Consider a small helper in `vista`, for example `DebugModeUiBinder`, that can:

- create styled debug icon buttons
- register/unregister property listeners
- update header action panels

Keep fake-state ownership inside each panel.

## Verification

- `mvn -q -DskipTests compile`
- Toggle Debug mode and verify debug controls appear/disappear and fake state clears.

## Related Docs

- `docs/pipelines/debug-mode-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
