# Automation Tab UI Process

## Status

Completed

## Linked Feature

- `docs/features/automation-tab-ui.md`

## Scope

Implement the visual Automation tab for the selected server using the existing automation backend. The feature covers lifecycle and command rule listing, creation, editing, enabling/disabling, deletion, validation, persistence, and scheduler refresh through `GestorServidores`.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked application shell, server lifecycle, UI component, model/data, and completed feature documentation workflows. |
| DONE | 2. Inspect automation contracts | Confirmed rule fields, supported actions/triggers, validation rules, and manager persistence APIs. |
| DONE | 3. Implement the Automation tab | Replaced the placeholder panel with lifecycle columns, a command table, and rule editor dialogs. |
| DONE | 4. Verify behavior | Compiled, ran the focused automation service tests, inspected diffs, and updated completed feature documentation. |

## Implementation Notes

The UI should not edit `ServerAutomationRule` instances from the server list directly. Dialogs work from cloned rules and save through `GestorServidores.guardarReglaAutomatizacion(...)` only after `ServerAutomationService.validateRule(...)` accepts the draft.

The lifecycle card groups rules by action type into `Apagados`, `Encendidos`, and `Reinicios`. Command rules use a compact `JTable` with an editable active checkbox, command text, trigger description, and recurrence description. Interval controls expose seconds, minutes, and hours; after-start command margins expose seconds and minutes.

## Verification Notes

- `mvn -q -DskipTests compile` passed with the expected Lombok `sun.misc.Unsafe` warning.
- `mvn -q "-Dtest=ServerAutomationServiceTest" test` passed with the expected Lombok `sun.misc.Unsafe` warning.
