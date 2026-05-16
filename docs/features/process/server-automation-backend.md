# Server Automation Backend Process

## Status

Completed

## Linked Feature

- `docs/features/server-automation-backend.md`

## Scope

Implement the non-UI automation logic for starts, stops, restarts, and specific console commands. Add persistence-ready rule models, runtime scheduling, program-start triggers, daily time triggers, interval triggers, and a minimal navigation shell for the future Automation tab.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked application shell, server lifecycle, console, and model/data pipelines before touching lifecycle and navigation code. |
| DONE | 2. Locate lifecycle hooks | Confirmed `GestorServidores` owns start, graceful stop, command sending, and restart-pending behavior. |
| DONE | 3. Implement backend logic | Added automation rule models, scheduler service, persistence APIs, program-start scheduling, and a first-class restart method. |
| DONE | 4. Wire minimal tab shell | Added the lazy Automation page and CPU navigation icon without implementing rule editing controls. |
| DONE | 5. Verify behavior | Compiled and ran focused tests for scheduler validation and dispatch. |

## Implementation Notes

Automation rules are stored on `modelo.Server` as persistent data. Runtime scheduling lives in `controlador.automation.ServerAutomationService`, which uses a daemon single-thread scheduler and looks up rules by server/rule id before execution so edited or removed rules do not keep stale captured objects.

The scheduler supports:

- `APP_START`: executed once when `GestorServidores.iniciarAutomatizaciones()` starts the service.
- `DAILY_TIME`: scheduled for the next matching `HH:mm`, then rescheduled after each run.
- `INTERVAL`: scheduled in seconds, minutes, or hours. Restart and command intervals repeat; stop intervals are uptime-based one-shot timers.

Actions are dispatched through `GestorServidores` so future UI and automation share the same lifecycle behavior.

## Verification Notes

- `mvn -q -DskipTests compile` passed with the expected Lombok `sun.misc.Unsafe` warning.
- `mvn -q "-Dtest=ServerAutomationServiceTest" test` passed with the expected Lombok `sun.misc.Unsafe` warning.
