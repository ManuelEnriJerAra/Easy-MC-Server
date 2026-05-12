# Server Port Conflict UI Desync

## Status

Pending

## Area

`controlador.GestorServidores`, `vista.PanelConfigServidor`, server start and `server.properties` synchronization.

## Issue

When Easy MC Server is force-closed while a Minecraft server process remains alive, reopening the app and starting the same server can detect the saved port as occupied. The occupied-port dialog lets the user save an alternate port before startup. That alternate port is written to `server.properties` and persisted in the server config, but the currently rendered configuration UI can keep showing the old port until the app or panel reloads.

This creates a visible mismatch: the properties file and next boot use the new port, while the active UI can imply the default or previous port is still configured.

## Desired Behavior

After accepting an alternate port from the occupied-port dialog, every visible UI surface for the selected server should reflect the new port in the same session.

The startup pipeline should keep `server.properties`, `ServerConfig`, persisted JSON, and active UI editors synchronized. If the config panel has unsaved edits, the fix should avoid silently discarding them and should define whether the automatic port update reloads only the affected field, prompts, or defers with a clear dirty-state indication.

## Evidence

- `GestorServidores.iniciarServidor(...)` synchronizes from `server.properties`, resolves a startup port, calls `serverConfig.setPuerto(puerto)`, writes the new value through `Utilidades.escribirPuertoEnProperties(...)`, and persists with `guardarServidor(server)`.
- The same startup path notifies `estadoServidor` after the process starts, but does not notify `configuracionServidor` immediately after the automatic port change.
- `GestorServidores.notificarConfiguracionServidor(...)` exists and is used by `PanelConfigServidor.save(...)`, but the automatic occupied-port rewrite does not use it.
- `PanelConfigServidor.reload()` reads `server.properties` and recreates editors, including `server-port`, but the panel only reloads on construction, manual reload, or callbacks from world changes. It does not listen to `configuracionServidor` or `estadoServidor` itself.
- `PanelPrevisualizacion` listens to `configuracionServidor` and `estadoServidor` for selected-server display refreshes, so at least some UI surfaces are already event-driven while the configuration editor is not.
- User symptom: after choosing a new port in the occupied-port dialog, the UI still shows the previous/default port, while `server.properties` appears updated and the next app boot uses the new value.

## Suggested Approach

Keep the source of truth explicit in the startup pipeline:

- After the automatic port rewrite succeeds, publish a configuration-change event for that server, not only a runtime-state event.
- Make `PanelConfigServidor` react to selected-server configuration changes while preserving dirty-state rules. If there are no unsaved edits, reload or update the `server-port` editor from disk/model. If there are unsaved edits, avoid overwriting the whole form silently and surface the external port update in a predictable way.
- Consider centralizing a small helper for "apply server port to model, properties file, persistence, and notifications" so manual config saves and automatic startup port changes cannot drift.
- Keep the occupied-port dialog copy and behavior Spanish/localized consistently if the flow is touched.

## Verification

- Start with a server configured on `25565`.
- Leave another process occupying `25565`, or simulate a leftover server process from a force-close.
- Open the app, start that same server, accept a suggested/custom alternate port, and confirm without restarting the app that:
  - `server.properties` contains the selected `server-port`.
  - The selected server's `ServerConfig.puerto` matches the selected port.
  - The configuration panel shows the selected port after the dialog/start pipeline completes.
  - Any IP/preview display using the server port shows the selected port.
- Repeat while the configuration panel has unrelated unsaved edits and confirm the fix does not silently discard user changes.
- Run `mvn -q -DskipTests compile`.
- Run targeted tests around `GestorServidores`/configuration behavior if adding or changing notification logic.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/configuration-pipeline.md`
- `docs/pipelines/application-shell-pipeline.md`
