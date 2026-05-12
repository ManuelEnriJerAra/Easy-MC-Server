# Debug Mode Pipeline

Debug mode is controlled globally by `src/main/java/vista/DebugMode.java`.

## Toggle Source

`VentanaPrincipal` toggles Debug mode after repeated clicks on the Info navigation entry. When enabled, the window title includes `(debug)`.

## Listener Pattern

Panels that expose debug-only controls should:

1. Register a `PropertyChangeListener` on `DebugMode.PROPERTY_ENABLED`.
2. Use `SwingUtilities.invokeLater(...)` before mutating Swing UI from the listener.
3. Show or hide debug controls when Debug mode changes.
4. Remove the listener when the component becomes undisplayable.

## Fake State Rules

Debug mode fake state should be in-memory only.

- Do not write fake state into server files.
- Do not mutate real logs, player data, config, or world data.
- Clear fake state when Debug mode is disabled.
- Keep fake state small and bounded so the UI can exercise edge cases without overwhelming real views.

## Current Implementations

`PanelJugadores`

- Adds plus/minus header buttons in Debug mode.
- Maintains `jugadoresDebug`.
- Merges fake players into the rendered player list.

`PanelMundo`

- Adds plus/minus header buttons in "Ultimas conexiones".
- Maintains `conexionesDebug`.
- Merges fake recent connections ahead of real/fallback entries.
- Caps displayed recent connections at the same limit used by real data.

## Button Style

Use:

- `AppTheme.applyHeaderIconButtonStyle(...)`
- `SvgIconFactory.apply(...)`
- `easymcicons/plus.svg`
- `easymcicons/minus.svg`

Always set tooltips for debug controls.
