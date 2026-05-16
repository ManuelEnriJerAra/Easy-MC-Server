# System Tray Process

## Status

Implemented

## Linked Feature

- `docs/features/system-tray.md`

## Scope

Implement a local system tray integration for the main Dora window from `docs/pending-features/system-tray.md`. The feature covers hiding `VentanaPrincipal` to the tray when supported, restoring the window from tray actions, showing current active server information in the tray menu, and moving the active-server shutdown confirmation to an explicit quit action. The no-tray fallback keeps the existing safe exit confirmation path.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `AGENTS.md`, `docs/README.md`, and the application shell, server lifecycle, and UI component pipeline guides. |
| DONE | 2. Implement tray behavior | Updated `VentanaPrincipal` close handling, tray installation, menu refresh, restore, and explicit quit flow. |
| DONE | 3. Complete feature docs | Moved the pending request into `docs/features/system-tray.md`, updated the shell pipeline, and deleted the pending feature note. |
| DONE | 4. Verify behavior | Compiled with Maven using JDK 25 and reviewed the changed git diff. |

## Implementation Notes

- `SystemTray` and `TrayIcon` are AWT APIs; all Swing window mutations should be marshalled back to the EDT.
- `GestorServidores.getServidoresActivos()` already provides the runtime server list needed by the tray menu.
- `GestorServidores.detenerServidoresActivosParaSalir()` remains the shutdown mechanism for explicit quit.
- `VentanaPrincipal` now installs the tray icon only when `SystemTray.isSupported()` succeeds. When tray support is unavailable or installation fails, the window close button keeps using the explicit quit flow.
- The tray menu is rebuilt from current `GestorServidores` state when server state/list events arrive and again around popup-trigger mouse events.
- Each active-server row appends `RAM: used% / max`, using `PanelEstadisticas.getLiveResourceSnapshot(...)` for the latest sampled percentage and the server config for the maximum RAM.
- `VentanaPrincipal` registers its `GestorServidores` with the statistics sampler when tray support is installed so tray RAM percentages can update even before the statistics panel is opened.
- Tray server rows use an ellipsized fixed-width name column and a right-aligned RAM value column. Because AWT `PopupMenu` items are plain text, active server rows use a monospaced font to keep those columns aligned.

## Verification Notes

- `mvn -q -DskipTests compile`
- The compile command was run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.
- Manual tray behavior still needs an interactive desktop smoke test on Windows.
