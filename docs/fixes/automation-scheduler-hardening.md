# Automation Scheduler Hardening

## Status

Fixed

## Original Issue

Daily automation tasks could duplicate after a schedule refresh, duplicate rule IDs could leave old futures uncancelled, and malformed persisted automation lists containing null entries could crash rule readers.

## Root Cause

One-shot daily tasks rescheduled themselves without checking whether they belonged to the current scheduler refresh. Rule normalization also preserved duplicate IDs and null list entries.

## Solution

Added a scheduler generation guard to `ServerAutomationService`, cancelled replaced futures defensively, de-duplicated automation rule IDs during manager/model normalization, and filtered null automation rules before returning copies to callers.

## Files Changed

- `src/main/java/controlador/automation/ServerAutomationService.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/modelo/Server.java`
- `src/test/java/controlador/ServerAutomationServiceTest.java`
- `docs/fixes/process/automation-scheduler-hardening.md`
- `docs/fixes/automation-scheduler-hardening.md`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=ServerAutomationServiceTest" test`

## Detailed Process

- `docs/fixes/process/automation-scheduler-hardening.md`

## Regression Notes

If future scheduler paths add one-shot tasks that reschedule themselves, pass through the same generation guard or an equivalent cancellation token. Persisted automation lists should remain null-free and ID-unique before scheduling.

## Related Docs

- `docs/features/server-automation-backend.md`
- `docs/features/automation-relative-triggers.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
