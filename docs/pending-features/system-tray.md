# System Tray

## Status

Pending

## Area

`vista.VentanaPrincipal`, application shell/window lifecycle, and `controlador.GestorServidores` active-server shutdown flow.

## Feature Request

Add a system tray integration for Easy MC Server, with Docker-like behavior:

- closing the main frame should no longer mean exiting the program
- the app should continue running from the system tray while servers keep running
- explicit exit should move to a tray action such as `Salir` or `Cerrar Easy MC Server`
- the tray right-click menu should expose useful runtime information, especially which managed servers are currently live

## Motivation

The app manages long-running Minecraft server processes. A user may reasonably want to close or hide the main window without stopping the app or being warned about active servers every time they press the window close button.

This is similar to Docker Desktop-style desktop behavior: closing the visible window does not necessarily quit the background manager; quitting is an explicit tray/menu action.

## Desired Behavior

The main window close button should hide the frame to the tray instead of running the current full-exit flow. It should not check whether servers are still running, because closing the frame would no longer close the application.

The active-server confirmation and shutdown behavior should move to explicit program exit actions, especially the tray menu exit action.

Expected tray behavior:

- show an Easy MC Server tray icon while the app is running, when `SystemTray.isSupported()` is true
- left click or double click restores/focuses `VentanaPrincipal`
- right click opens a tray menu
- tray menu includes an action to restore/open the main window
- tray menu includes active-server status, for example `Servidores activos: 2`
- tray menu lists live servers by display name when any are running
- tray menu includes an explicit quit action
- choosing quit runs the same safety flow currently used by `intentarCerrarAplicacion(...)`: warn about active servers, list them, allow cancel, and stop active servers before `System.exit(0)`
- if no tray support exists, keep a safe fallback where closing the frame still exits through the current confirmation flow

The existing unsaved configuration guard should still apply when switching away from configuration views or when explicitly exiting. It should not block a simple hide-to-tray action unless there is a specific risk of losing in-progress configuration edits.

## Notes

- Current frame close flow is in `src/main/java/vista/VentanaPrincipal.java`.
- `windowClosing(...)` currently calls `intentarCerrarAplicacion(gestorServidores)`.
- `intentarCerrarAplicacion(...)` currently checks `gestorServidores.getServidoresActivos()`, prompts with the active server list, calls `gestorServidores.detenerServidoresActivosParaSalir()`, disposes the window, and calls `System.exit(0)`.
- Active server detection already exists in `GestorServidores.getServidoresActivos()`.
- Active server shutdown already exists in `GestorServidores.detenerServidoresActivosParaSalir()`.
- The implementation should avoid duplicating shutdown logic between the frame and tray.
- The tray menu should be refreshed when opened or rebuilt from current state so live server information is not stale.
- Use the project icon/logo if suitable for `TrayIcon`; otherwise add or derive a small tray-specific image asset.
- Java `SystemTray`, `TrayIcon`, and `PopupMenu` are AWT APIs, so Swing updates/restores should still be marshalled onto the EDT.
- System tray support can behave differently across Windows, Linux desktop environments, and macOS; the feature needs graceful fallback behavior.

## Suggested Approach

1. Extract the current application-exit flow from `VentanaPrincipal.intentarCerrarAplicacion(...)` into a method that means explicit quit, not window close.
2. Add a separate `ocultarEnBandeja()` or similar frame-close path that hides the window when tray support is active.
3. Add a tray installation helper owned by `VentanaPrincipal` or a small focused helper class in `vista`, depending on how much code is needed.
4. Build a tray `PopupMenu` with:
   - open/restore action
   - active server count
   - disabled menu items for currently running server names
   - separator
   - quit action
5. Make the quit action call the extracted explicit-exit method.
6. Remove the tray icon when the application truly exits.
7. Keep listener cleanup behavior intact, including `DebugMode.removePropertyChangeListener(...)` when the frame is disposed.
8. Consider a first-run notification or status message so users understand the app is still running after closing the window.

## Verification

- `mvn -q -DskipTests compile`
- Manual Windows smoke test:
  - start the app
  - close the main frame and confirm it hides without prompting about active servers
  - restore from the tray icon
  - start one or more servers and confirm tray menu shows active server names/count
  - choose tray quit, confirm active-server warning appears, cancel, and verify app/servers keep running
  - choose tray quit again, accept shutdown, and verify servers stop and process exits
- Manual fallback test or code path review for `SystemTray.isSupported() == false`.
- Verify unsaved configuration prompts still appear when navigating away or explicitly exiting.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
