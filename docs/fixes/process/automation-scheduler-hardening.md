# Automation Scheduler Hardening Process

## Status

Completed

## Linked Fix

- `docs/fixes/automation-scheduler-hardening.md`

## Scope

Harden automation scheduling against duplicate daily reschedules, duplicate rule IDs, and malformed persisted rule lists containing null entries.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Review risks | Identified duplicate daily rescheduling, duplicate rule IDs, and null persisted rules as commit-blocking edge cases. |
| DONE | 2. Implement guardrails | Added scheduler generations, defensive future replacement cancellation, rule ID de-duplication, and null rule filtering. |
| DONE | 3. Verify behavior | Compiled and ran the focused automation service tests. |

## Implementation Notes

`ServerAutomationService` increments a generation number whenever schedules are refreshed. One-shot daily tasks remember the generation they were scheduled under and only reschedule themselves when that generation is still current.

Automation rule normalization now removes null entries and regenerates duplicate IDs before persistence or scheduler refreshes can consume the list.

## Verification Notes

- `mvn -q -DskipTests compile` passed with the expected Lombok `sun.misc.Unsafe` warning.
- `mvn -q "-Dtest=ServerAutomationServiceTest" test` passed with the expected Lombok `sun.misc.Unsafe` warning.
