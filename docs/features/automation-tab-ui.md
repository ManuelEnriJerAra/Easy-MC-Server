# Automation Tab UI

## Status

Implemented

## Feature

Added a full Automation tab for managing selected-server automation rules from the Swing UI.

## Motivation

Users need a visual way to configure automated server starts, restarts, stops, and console commands without editing persisted JSON manually.

## Solution

`PanelAutomatizacion` now renders two `CardPanel` sections: lifecycle automation and command automation. Lifecycle rules are grouped into `Apagados`, `Encendidos`, and `Reinicios`, with compact rows for scheduled daily times, uptime-based stops, repeated restarts, and app-start triggers. Interval and relative-margin controls support seconds where short timings are useful. Daily times are edited with bounded hour/minute controls instead of free text. Command rules are shown in a dense table with active state, command text, trigger timing, and recurrence, including relative triggers before stops/restarts and after starts.

The UI can create, edit, enable, disable, and delete rules. Editors validate drafts through `ServerAutomationService.validateRule(...)` and persist only through `GestorServidores.guardarReglaAutomatizacion(...)` or `eliminarReglaAutomatizacion(...)`, so the scheduler refreshes through the existing manager flow.

## Files Changed

- `src/main/java/vista/PanelAutomatizacion.java`
- `docs/features/process/automation-tab-ui.md`
- `docs/features/automation-relative-triggers.md`
- `docs/features/automation-tab-ui.md`
- `docs/pipelines/application-shell-pipeline.md`
- `docs/features/server-automation-backend.md`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=ServerAutomationServiceTest" test`

## Detailed Process

- `docs/features/process/automation-tab-ui.md`

## Follow-Up Notes

The backend currently supports app-start, daily-time, and interval triggers. A distinct server-start trigger should be added to the backend before exposing "Inicio del servidor" as a selectable UI option.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
- `docs/features/server-automation-backend.md`
