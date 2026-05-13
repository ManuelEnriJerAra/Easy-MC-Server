# Installed Extension Favorite Manual Order Resets

## Status

Pending

## Area

Installed mods/plugins list ordering, favorite presentation state, extension cache persistence.

## Issue

Trying to manually sort favorite installed mods/plugins does not persist. After refresh or resync, extensions go back to their previous place. The installed extension pipeline currently treats filesystem detection order and cache normalization as canonical, so any UI-only manual order is overwritten.

## Desired Behavior

Favorite installed extensions should keep a stable user-defined order across refreshes, rescans, app restarts, install/remove operations, and metadata cache writes. Non-favorite extensions should still have a predictable fallback order, such as display name or relative path.

## Evidence

- `PanelExtensiones` stores visible installed extensions only in `DefaultListModel<ServerExtension>` and reloads the model from `gestorServidores.sincronizarExtensionesInstaladas(server)`.
- `PanelExtensiones.ReloadExtensionsWorker.done()` clears `extensionsModel` and repopulates it from the service result, so any UI-only reorder is lost on reload.
- `GestorServidores.sincronizarExtensionesServidor(...)` calls `serverExtensionsService.detectInstalledExtensions(server)` and then replaces `server.extensions` with the detected list.
- `ServerExtensionsService.detectInstalledExtensions(...)` scans extension directories and sorts discovered `.jar` files by file name.
- `InstalledExtensionsCacheService.normalizeExtensions(...)` deduplicates cached extensions and sorts them by relative path before returning/persisting.
- `ServerExtension` and `ExtensionLocalMetadata` do not currently expose fields equivalent to server-level `favorito`, `ordenLista`, or `ordenFavorito`.
- `modelo.Server` already has `favorito`, `ordenLista`, and `ordenFavorito`, which is a useful precedent for separating user presentation state from discovered filesystem state.

## Suggested Approach

Add explicit extension presentation metadata, likely in `ExtensionLocalMetadata`, such as:

- `favorito`
- `ordenLista` or `ordenFavorito`

Then update the extension pipeline:

- Preserve favorite/order fields when merging detected extensions with cached or existing server extensions.
- Sort installed extensions by favorite/order first, then fall back to stable display name or relative path.
- Avoid having `InstalledExtensionsCacheService.normalizeExtensions(...)` discard user order by always sorting only by relative path.
- Add or update UI affordances for manually reordering favorites if the current interaction is incomplete or only visual.
- Persist the reordered state through the installed extension cache and/or server metadata consistently.

## Verification

- Mark several installed mods/plugins as favorites, manually reorder them, refresh the list, and confirm the order remains.
- Restart the app and confirm favorite extension order remains.
- Install or remove an extension and confirm existing favorite order is preserved.
- Rescan installed extensions and confirm detected metadata updates do not overwrite favorite/order fields.
- Run targeted tests:

```bash
mvn -q -Dtest=ServerExtensionsServiceTest,GestorServidoresTest,PanelExtensionesTest test
```

- Run at least:

```bash
mvn -q -DskipTests compile
```

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
