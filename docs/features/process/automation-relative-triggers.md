# Automation Relative Triggers Process

## Status

Completed

## Linked Feature

- `docs/features/automation-relative-triggers.md`

## Scope

Add command automation triggers that run relative to lifecycle automation rules: before scheduled stops, before scheduled restarts, and after scheduled starts. The feature covers model triggers, scheduler behavior, validation, UI selection, tests, and documentation.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read automation context | Reviewed the existing automation model, scheduler, UI editor, and focused tests. |
| DONE | 2. Implement relative triggers | Added trigger enum values, scheduler anchoring to lifecycle rules, UI controls for offsets, and validation. |
| DONE | 3. Verify behavior | Compiled and ran the focused automation service tests. |

## Implementation Notes

Relative triggers are command triggers. `BEFORE_STOP` and `BEFORE_RESTART` can use seconds, minutes, or hours as the offset. `AFTER_START` can use seconds or minutes.

Relative command rules anchor to enabled lifecycle rules on the same server:

- `BEFORE_STOP` anchors to enabled `STOP` rules.
- `BEFORE_RESTART` anchors to enabled `RESTART` rules.
- `AFTER_START` anchors to enabled `START` rules.

The scheduler supports daily and interval lifecycle anchors. Each relative command rule may create one scheduled task per matching lifecycle anchor.

## Verification Notes

- `mvn -q -DskipTests compile` passed with the expected Lombok `sun.misc.Unsafe` warning.
- `mvn -q "-Dtest=ServerAutomationServiceTest" test` passed with the expected Lombok `sun.misc.Unsafe` warning.
