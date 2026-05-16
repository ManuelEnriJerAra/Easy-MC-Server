# Extension Queue Dependency Resolution Feedback Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extension-queue-dependency-resolution-feedback.md`

## Scope

Fixes the marketplace queue feeling unresponsive after the user adds an extension with dependencies or optional complements. The first click should be represented immediately in the queue while dependency resolution continues in the background.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Locate the queue path | The add action started workers, but no queue row appeared until plan and dependency resolution completed. |
| DONE | 2. Add visible preparation state | Added a `RESOLVING` queue state and immediate queue placeholder for the clicked extension. |
| DONE | 3. Preserve click intent | The background worker now updates the same placeholder into a pending or failed item, and user removal prevents stale worker results from re-adding it. |
| DONE | 4. Verify behavior | Added targeted tests for the resolving placeholder/admission path and ran compile. |

## Implementation Notes

Dependency resolution can still contact remote providers and walk optional branches, so the durable fix is to make that work visible and cancellable in the queue. The placeholder is treated as active queue state for result decorations, but it is not processed by the install queue until resolution promotes it to `PENDING`.

## Verification Notes

- `mvn -q -Dtest=ExtensionMarketplaceDependencyTest test`
- `mvn -q -DskipTests compile`
