# Server List Refresh Keeps Deleted Folders

## Status

Pending

## Area

`vista.VentanaPrincipal`, `vista.PanelServidores`, and `controlador.GestorServidores` server list refresh/persistence.

## Issue

The server list refresh action can keep showing servers whose folders were deleted outside the app. A user deleted all server folders, clicked refresh, and the deleted servers still appeared in the left server list.

The current refresh path appears to redraw the existing in-memory server list instead of reloading or revalidating persisted server entries against the filesystem.

## Desired Behavior

Refreshing the server list should reconcile the visible and persisted server list with the actual filesystem state:

- Servers whose folders no longer exist should disappear from the visible list after refresh.
- The selected server should be cleared or moved to a valid remaining server if the selected server folder was deleted.
- The app should persist the cleaned list so deleted-folder entries do not reappear after navigation or restart.
- If invalid entries are removed, the UI should notify the user consistently with existing invalid-server messaging.

## Evidence

- User report: after deleting all server folders, "refresh servers" does not remove them from the app; they keep appearing.
- `src/main/java/vista/VentanaPrincipal.java` wires the header refresh button with `refrescarListaServidoresButton.addActionListener(e -> listaServidoresPanel.refrescarListado())`.
- `src/main/java/vista/PanelServidores.java` implements `refrescarListado()` by rebuilding from `gestorServidores.getListaServidores()`.
- `src/main/java/controlador/GestorServidores.java` has validation/removal logic near `comprobarServidoresGuardados(...)`, but that cleanup appears tied to load/startup-style validation rather than the manual refresh button.

## Suggested Approach

- Add a manager-level refresh/reconcile method in `GestorServidores` that checks each saved server's folder and validity, removes stale entries, updates selection, persists the list, and fires the normal server-list property change.
- Make `VentanaPrincipal` call that manager refresh method instead of only asking `PanelServidores` to redraw.
- Reuse the existing invalid-server summary/copy where possible so manual refresh and startup validation behave consistently.
- Keep the destructive boundary clear: this refresh should remove stale app entries only; it must not delete files.

## Verification

- Add or update `GestorServidoresTest` coverage for removing saved servers whose directories no longer exist during a manual refresh/reconcile call.
- Add coverage or a manual check that the selected server is cleared when its folder was removed.
- Manual UI check: create/import multiple servers, delete their folders outside the app, click the server-list refresh button, and confirm the list becomes empty and stays empty after restart.
- Run at least:

```bash
mvn -q -DskipTests compile
```

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/application-shell-pipeline.md`
