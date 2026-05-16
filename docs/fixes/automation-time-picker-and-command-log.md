# Automation Time Picker And Command Log

## Status

Fixed

## Original Issue

Automation daily-time rules used a free text field for `HH:mm`, leaving users to type a valid time manually. Automated command rules also produced both the automation execution log and the normal manual command-sent log.

## Root Cause

The rule editor reused `FlatTextField` for time input, and `ServerAutomationService` dispatched command actions through the default `mandarComando(...)` overload that appends the command-sent console message.

## Solution

Added a bounded hour/minute picker inside `PanelAutomatizacion` and changed automation command dispatch to call `mandarComando(server, command, false)`.

## Files Changed

- `src/main/java/vista/PanelAutomatizacion.java`
- `src/main/java/controlador/automation/ServerAutomationService.java`
- `src/test/java/controlador/ServerAutomationServiceTest.java`
- `docs/fixes/process/automation-time-picker-and-command-log.md`
- `docs/fixes/automation-time-picker-and-command-log.md`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=ServerAutomationServiceTest" test`

## Detailed Process

- `docs/fixes/process/automation-time-picker-and-command-log.md`

## Regression Notes

Daily-time automation editors should keep producing normalized `HH:mm` strings without relying on user-entered text. Automated commands should avoid the manual command-sent console line unless the user explicitly sends a command from the console.

## Related Docs

- `docs/pipelines/console-and-players-pipeline.md`
- `docs/features/automation-tab-ui.md`
