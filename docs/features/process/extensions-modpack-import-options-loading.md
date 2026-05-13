# Modpack Import Options And Loading Process

## Status

Completed

## Linked Feature

- `docs/features/extensions-modpack-import-options-loading.md`

## Scope

Add an explicit Modrinth modpack import options dialog and modal loading dialogs while import preflight, downloads, and installation run. The user should choose whether to import server, client, or complete content.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read current import flow | `PanelExtensiones` currently reads `.mrpack`, shows a generic confirmation, then starts `ImportModpackWorker` without a loading dialog. |
| DONE | 2. Implement import options | Added Modrinth import option records/service filtering and controller wiring. |
| DONE | 3. Add loading dialogs | Added modal loading dialogs while preflight and the import worker run, and aligned existing blocking task dialogs to the same shared progress bar style. |
| DONE | 4. Verify and document | Ran targeted tests/full suite and added completed feature docs. |

## Implementation Notes

- The import path should not infer the desired content side silently. Server/client/complete selection is explicit, defaults to complete, and all files compatible with the selected side are included.
- `ImportMode.CLIENT` must not be blocked by server-side checks before the user-selected filter runs.
- Loading dialogs use the shared `AppTheme.createLoadingProgressBar(...)` helper so import/download flows stay visually consistent.
- Pack metadata reading and installed-extension synchronization run before the wizard in a background worker so large preflight work does not block the Swing event thread.

## Verification Notes

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -DskipTests compile`
- `mvn test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.
