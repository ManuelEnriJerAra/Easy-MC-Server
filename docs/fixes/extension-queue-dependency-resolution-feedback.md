# Extension Queue Dependency Resolution Feedback

## Status

Fixed

## Original Issue

Adding a marketplace extension with dependencies or optional complements could look unresponsive because the queue did not show the clicked extension until dependency resolution finished.

## Root Cause

The click started background workers, but the visible queue row was only created after download-plan and dependency resolution completed. Slow provider calls made it look like the click had not registered.

## Solution

The marketplace now creates a removable `RESOLVING` queue item immediately when the user adds an extension. Dependency resolution updates that same item to pending or failed when it finishes, and stale worker results are ignored if the user removes the resolving item first.

## Files Changed

- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `src/main/java/vista/ExtensionMarketplaceDialogModels.java`
- `src/test/java/vista/ExtensionMarketplaceDependencyTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -Dtest=ExtensionMarketplaceDependencyTest test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/extension-queue-dependency-resolution-feedback.md`

## Regression Notes

Queue actions that perform remote work should create visible, removable queue state before the remote work finishes. Do not require users to click add again after a background dependency worker completes.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
