# Server Selection Lazy Right Panel Loading Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-selection-lazy-right-panel-loading.md`

## Scope

Keep server selection responsive by stopping the app from eagerly constructing all right-side pages on every selection change. Leave broader page-internal caching and background loading for a separate improvement.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Trace the selection path | Followed `PanelServidores -> VentanaPrincipal -> renderizarPanelDerecho` and confirmed the right side was rebuilt synchronously on the EDT for every selection. |
| DONE | 2. Identify the heavy pages | Found that `PanelMundo` and `PanelEstadisticas` do synchronous file and data work during construction, even when hidden. |
| DONE | 3. Implement the fix | Switched right-side page creation to lazy loading through card containers and on-demand panel construction. |
| DONE | 4. Document the broader debt | Added a pending-feature note for background loading and metadata caching work that remains out of scope here. |
| TO DO | 5. Verify behavior | Compile and manually confirm that server selection on Home no longer freezes or scroll-jumps, and that each page still loads on first navigation. |

## Implementation Notes

The fix keeps the existing page structure and navigation semantics, but changes when each page is instantiated. Home-specific console listener wiring now happens when the Home page is loaded instead of during every server-selection rebuild.

## Verification Notes

`mvn` was not available in this shell, so compile/test execution is still pending. Manual validation should focus on repeated selection from the left list and first-open behavior for Mundo, Configuración, Extensiones, and Estadísticas.
