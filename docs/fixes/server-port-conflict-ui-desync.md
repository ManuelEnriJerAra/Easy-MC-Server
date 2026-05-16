# Server Port Conflict UI Desync

## Status

Fixed

## Original Issue

After accepting an alternate port during startup, `server.properties` and `ServerConfig` changed but the visible configuration panel could keep showing the old `server-port`.

## Root Cause

The startup path saved the rewritten port but only emitted runtime-state notifications. `PanelConfigServidor` did not listen for configuration changes, so an already-rendered editor had no reason to refresh.

## Solution

`GestorServidores.iniciarServidor(...)` now fires `configuracionServidor` after the automatic startup port rewrite is saved.

`PanelConfigServidor` listens for selected-server configuration events while displayable. Clean forms reload from disk. Dirty forms preserve user edits and reconcile only the external `server-port` value unless the user has already edited that same field.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/vista/PanelConfigServidor.java`
- `docs/fixes/process/server-port-conflict-ui-desync.md`

## Verification

- `mvn -q "-Dtest=GestorServidoresTest,PanelMundoDebugConnectionsTest" test`
- `mvn -q -DskipTests compile`

Both passed. Maven emitted the expected `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/fixes/process/server-port-conflict-ui-desync.md`

## Regression Notes

Any automatic server config rewrite should emit `configuracionServidor` after persistence. Config editors should distinguish clean reloads from dirty-field reconciliation.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/configuration-pipeline.md`
- `docs/pipelines/application-shell-pipeline.md`
