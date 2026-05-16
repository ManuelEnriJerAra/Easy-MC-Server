# Server List Scroll Reset On Start Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-list-scroll-reset-on-start.md`

## Scope

Keep the server-list scroll position stable when a server is started from the right-click context menu. This covers the list rebuild caused by the start flow saving server settings and does not change the server start lifecycle itself.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Trace right-click start path | Reviewed `PanelServidores.crearMenuServidor(...)` and `GestorServidores.iniciarServidor(...)`; startup saves the server and emits `listaServidores`. |
| DONE | 2. Identify scroll reset cause | Found `PanelServidores.recargarPanel(...)` restored the scroll position immediately after replacing the viewport view, when the new view could still have an unset size. |
| DONE | 3. Implement stable restore | Restored using preferred size as a fallback and scheduled a second restore after Swing layout. |
| DONE | 4. Add regression coverage | Added a `PanelServidoresTest` case that scrolls a long list, triggers a list refresh, and verifies the Y position is preserved. |
| DONE | 5. Verify behavior | Ran the focused test and compile. |

## Implementation Notes

The selected row is restored with `mostrarSeleccionServidor(...)`, which avoids forcing the row visible. The final scroll position is then restored from the saved viewport point.

The immediate restore still helps when dimensions are already available. The deferred restore handles the common Swing case where the replacement viewport view has not been laid out yet.

## Verification Notes

- `mvn -q -Dtest=PanelServidoresTest test` passed.
- `mvn -q -DskipTests compile` passed.
- Maven emitted the expected Lombok `sun.misc.Unsafe` warning.
