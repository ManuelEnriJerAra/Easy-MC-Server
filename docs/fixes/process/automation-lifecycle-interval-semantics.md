# Automation Lifecycle Interval Semantics Process

## Status

Completed

## Linked Fix

- `docs/fixes/automation-lifecycle-interval-semantics.md`

## Scope

Clean up odd lifecycle automation semantics so interval stops are uptime-based, repeated restarts remain interval-based, and automations skip quietly when the selected action cannot use the current server runtime state.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Review current semantics | Confirmed interval lifecycle rules were all fixed-rate and actions attempted work even when server state made them useless. |
| DONE | 2. Implement cleanup | Added uptime-based stop scheduling, state-aware skips, start-interval validation, relative interval offset checks, and UI wording updates. |
| DONE | 3. Verify behavior | Compiled and ran focused automation service tests. |

## Implementation Notes

`STOP + INTERVAL` now schedules a one-shot stop only while the server is observed as running. `RESTART + INTERVAL` remains a fixed-rate repeated restart. `START + INTERVAL` is invalid.

The automation service receives server state notifications through `GestorServidores.notificarEstadoServidor(...)`, which lets it schedule or cancel uptime-stop timers as servers start and stop.

## Verification Notes

- `mvn -q -DskipTests compile` passed with the expected Lombok `sun.misc.Unsafe` warning.
- `mvn -q "-Dtest=ServerAutomationServiceTest" test` passed with the expected Lombok `sun.misc.Unsafe` warning.
