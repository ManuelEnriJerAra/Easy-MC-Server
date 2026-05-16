# System Tray

## Status

Implemented

## Feature

Dora can keep running from the system tray when the main server-management window is closed.

## Motivation

Managed Minecraft servers can be long-running processes. Closing the main frame should be a hide/minimize action when the tray is available, while quitting the background manager should be an explicit user action.

## Solution

- Added system tray installation to `VentanaPrincipal` when `SystemTray.isSupported()` is true.
- Changed the main window close button so it hides the frame to the tray instead of running the full application exit path.
- Added tray actions to restore the main window and explicitly quit Dora.
- Added a refreshed tray menu showing active server count and live server display names with per-server RAM usage.
- Active server rows ellipsize long names and align RAM values in a fixed text column so long server names do not stretch the tray menu.
- Kept the existing active-server confirmation and shutdown flow for explicit quit and for the fallback path when tray support is unavailable.
- Reused `doraapp/logo.png` for the frame and tray icon, with tray removal during true application disposal.

## Files Changed

- `src/main/java/vista/VentanaPrincipal.java`
- `docs/pipelines/application-shell-pipeline.md`
- `docs/features/process/system-tray.md`
- `docs/features/system-tray.md`
- `docs/pending-features/system-tray.md`

## Verification

- `mvn -q -DskipTests compile`

The compile command was run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.

## Detailed Process

- `docs/features/process/system-tray.md`

## Follow-Up Notes

- Perform an interactive Windows tray smoke test before release packaging, especially restore, right-click menu refresh, quit-cancel, and quit-confirm with active servers.
- Each active server row shows RAM as the latest sampled used percentage and the configured maximum RAM. Long server names are shortened in the tray menu and RAM values are right-aligned.
- System tray support varies by desktop environment, so the no-tray fallback should remain covered by code review when shell behavior changes.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
