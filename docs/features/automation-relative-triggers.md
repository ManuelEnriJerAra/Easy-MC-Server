# Automation Relative Triggers

## Status

Implemented

## Feature

Added relative command automation triggers for running commands before scheduled stops, before scheduled restarts, and after scheduled starts.

## Motivation

Server owners often need warning or setup commands around lifecycle automation, such as notifying players before an automated stop/restart or running a command after an automated start.

## Solution

Added `BEFORE_STOP`, `BEFORE_RESTART`, and `AFTER_START` to `AutomationTriggerType`. The scheduler now anchors enabled command rules with those triggers to matching enabled lifecycle rules on the same server. Daily lifecycle anchors are scheduled by applying the offset around the daily time, and interval lifecycle anchors are scheduled relative to each interval execution.

The Automation tab exposes these options for command rules. `Antes de apagado` and `Antes de reinicio` accept seconds, minutes, or hours. `Despues de encendido` accepts seconds or minutes. For interval lifecycle anchors, the relative offset must be smaller than the anchor interval.

## Files Changed

- `src/main/java/modelo/automation/AutomationTriggerType.java`
- `src/main/java/controlador/automation/ServerAutomationService.java`
- `src/main/java/vista/PanelAutomatizacion.java`
- `src/test/java/controlador/ServerAutomationServiceTest.java`
- `docs/features/process/automation-relative-triggers.md`
- `docs/features/automation-relative-triggers.md`
- `docs/features/automation-tab-ui.md`
- `docs/features/server-automation-backend.md`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=ServerAutomationServiceTest" test`

## Detailed Process

- `docs/features/process/automation-relative-triggers.md`

## Follow-Up Notes

Relative triggers currently anchor to automation-managed lifecycle rules, not manual button clicks. If manual start/stop/restart hooks need relative commands later, route them through a lifecycle event path instead of duplicating scheduling logic in the UI.

## Related Docs

- `docs/features/automation-tab-ui.md`
- `docs/features/server-automation-backend.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
