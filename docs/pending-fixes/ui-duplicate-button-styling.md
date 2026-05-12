# Duplicate Button Styling Logic

## Status

Pending

## Area

Swing UI action/header buttons.

## Issue

Multiple panels configure action buttons, icon hover states, and header/debug controls locally. Some reuse `AppTheme`, while others layer extra behavior in panel-specific helpers. This makes buttons easier to drift visually and behaviorally.

## Desired Behavior

Button styling should be centralized around `AppTheme` and small reusable helpers, with panel code only choosing icon, tooltip, and action.

## Evidence

- `PanelJugadores.configurarBotonDebug(...)`
- `PanelMundo.configurarBotonDebug(...)`
- `PanelExtensiones.configureExtensionActionButton(...)`
- `PanelConfigServidor.styleActionButton(...)`
- `PanelControlServidor.configurarBotonAccion(...)`

## Suggested Approach

Introduce or extend shared helpers for common button shapes:

- header icon button
- debug icon button
- primary action button
- extension/list row action button if needed

Adopt gradually when editing panels.

## Verification

- `mvn -q -DskipTests compile`
- Manual UI smoke test across players, worlds, config, extensions, and server controls.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/debug-mode-pipeline.md`
