# Automation Second Intervals Process

## Status

Completed

## Linked Feature

- `docs/features/automation-second-intervals.md`

## Scope

Add seconds as a supported automation interval unit across the model, scheduler validation, Automation tab controls, focused tests, and completed feature documentation.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Extend interval unit | Added seconds to `AutomationIntervalUnit` so scheduler duration math uses the same model path as minutes and hours. |
| DONE | 2. Update UI and validation | Exposed seconds in interval and relative controls, while keeping after-start margins limited to seconds or minutes. |
| DONE | 3. Verify behavior | Compiled and ran the focused automation service tests. |

## Implementation Notes

Seconds are available for interval rules and for before-stop/before-restart relative command margins. After-start relative command margins expose seconds and minutes only, so users can configure short post-start waits without creating hour-scale delayed commands.

## Verification Notes

- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=ServerAutomationServiceTest" test`
