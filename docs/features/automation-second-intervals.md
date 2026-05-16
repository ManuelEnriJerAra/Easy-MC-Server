# Automation Second Intervals

## Status

Implemented

## Feature

Automation intervals and relative margins now support seconds wherever minute-based short timings are useful.

## Motivation

Users may want fast local testing or short warning/setup windows without waiting a full minute.

## Solution

Added seconds as an `AutomationIntervalUnit`. Interval automation can now use seconds, minutes, or hours. Relative command triggers before stops and restarts also support seconds, minutes, or hours. Relative command triggers after starts support seconds or minutes.

## Files Changed

- `src/main/java/modelo/automation/AutomationIntervalUnit.java`
- `src/main/java/controlador/automation/ServerAutomationService.java`
- `src/main/java/vista/PanelAutomatizacion.java`
- `src/test/java/controlador/ServerAutomationServiceTest.java`
- `docs/features/process/automation-second-intervals.md`
- `docs/features/automation-second-intervals.md`
- `docs/features/automation-relative-triggers.md`
- `docs/features/automation-tab-ui.md`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=ServerAutomationServiceTest" test`

## Detailed Process

- `docs/features/process/automation-second-intervals.md`

## Related Docs

- `docs/features/automation-tab-ui.md`
- `docs/features/automation-relative-triggers.md`
- `docs/features/server-automation-backend.md`
