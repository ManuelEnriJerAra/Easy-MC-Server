# Server List Refresh Stale Deleted Folders

## Status

Fixed

## Original Issue

Clicking the server-list refresh button could keep showing saved servers whose folders had been deleted outside the app.

## Root Cause

The refresh button only rebuilt `PanelServidores` from the current in-memory list. Filesystem validation and stale-entry cleanup only ran during manager construction.

## Solution

`GestorServidores.refrescarServidoresGuardados()` now reuses persisted-server validation for manual refreshes, removes stale entries, persists the cleaned list, fires the normal list event, and clears the selected server if it was removed.

`VentanaPrincipal` now calls that manager refresh from the header button, shows the existing invalid-server warning, redraws the list, and clears stale right-panel state when needed.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/vista/VentanaPrincipal.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `docs/fixes/process/server-list-refresh-stale-deleted-folders.md`

## Verification

- `mvn -q "-Dtest=GestorServidoresTest,PanelMundoDebugConnectionsTest" test`
- `mvn -q -DskipTests compile`

Both passed. Maven emitted the expected `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/fixes/process/server-list-refresh-stale-deleted-folders.md`

## Regression Notes

Manual refreshes should go through `GestorServidores`, not only repaint the list panel, whenever persisted server validity can change outside the app.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/application-shell-pipeline.md`
