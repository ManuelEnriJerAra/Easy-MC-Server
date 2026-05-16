# Extension Dependency Order Warning Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-dependency-order-warning.md`

## Scope

Allow marketplace plugins to download/install even when required dependencies are unresolved, failed earlier in the queue, or scheduled in a different order. This keeps dependency information visible as a warning without turning it into a queue blocker.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect dependency gates | Found hard stops during queue preparation and queue processing in `ExtensionMarketplaceDialog`. |
| DONE | 2. Convert blockers to warnings | Unresolved required dependencies now warn and continue; queue dependency order checks now publish warning progress instead of throwing. |
| DONE | 3. Update dependency prompt behavior | Unresolved-only required dependencies are no longer treated as promptable queue additions, and required dependency prompts no longer cancel the selected plugin download. |
| DONE | 4. Document the pipeline rule | Updated the extension pipeline to make dependency warnings non-blocking for marketplace downloads. |
| DONE | 5. Verify behavior | Added regression coverage for warning-only dependency checks and ran targeted compile/test commands. |

## Implementation Notes

The queue still tries to resolve and add dependencies when it can. The change is limited to the marketplace queue: dependency status affects warning text and feedback, but not whether the selected plugin gets a download/install attempt.

`QueueProgress` gained a warning phase so dependency-order warnings can be shown while the queue keeps processing the same item.

## Verification Notes

- `mvn -q -Dtest=ExtensionMarketplaceDependencyTest test`
- `mvn -q -DskipTests compile`
- `mvn -q test`
