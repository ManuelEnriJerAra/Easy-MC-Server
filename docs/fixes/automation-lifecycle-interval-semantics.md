# Automation Lifecycle Interval Semantics

## Status

Fixed

## Original Issue

Interval shutdown rules tried to stop the server every X interval even when the server was already off, and several automation actions produced noisy no-op attempts when the current runtime state made the action useless.

## Root Cause

All interval lifecycle rules were scheduled as fixed-rate tasks, and automation execution did not check whether the server was running before dispatching stop, restart, or command actions.

## Solution

Changed interval shutdowns to uptime-based one-shot timers, kept interval restarts as repeated fixed-rate tasks, made invalid runtime-state actions skip quietly, rejected start-by-interval rules, and prevented relative interval offsets from being equal to or larger than their interval anchor.

## Files Changed

- `src/main/java/controlador/automation/ServerAutomationService.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/vista/PanelAutomatizacion.java`
- `src/test/java/controlador/ServerAutomationServiceTest.java`
- `docs/fixes/process/automation-lifecycle-interval-semantics.md`
- `docs/fixes/automation-lifecycle-interval-semantics.md`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=ServerAutomationServiceTest" test`

## Detailed Process

- `docs/fixes/process/automation-lifecycle-interval-semantics.md`

## Regression Notes

Interval shutdowns should mean "after this much observed uptime," not repeated shutdown attempts. Repeated interval behavior should remain limited to actions where repetition is meaningful, such as restarts or commands while the server is running.

## Related Docs

- `docs/features/automation-tab-ui.md`
- `docs/features/automation-relative-triggers.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
