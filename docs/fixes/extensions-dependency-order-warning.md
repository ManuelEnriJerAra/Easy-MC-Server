# Extension Dependency Order Warning

## Status

Fixed

## Original Issue

Marketplace plugins with required dependencies could fail to install when the selected plugin was processed before its dependency, or when a dependency could not be resolved automatically.

## Root Cause

`ExtensionMarketplaceDialog` treated required dependency status as a hard queue gate. A queued item failed before download if one of its required dependency keys had not completed yet, and unresolved required dependencies during queue preparation stopped the selected plugin from being queued.

## Solution

Dependency issues in the marketplace queue are now warnings instead of blockers. Unresolved required dependencies show a warning and the selected plugin still enters the queue. During queue processing, missing or previously failed dependency keys publish a warning but the plugin download/install attempt continues.

## Files Changed

- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `src/main/java/vista/ExtensionMarketplaceDialogModels.java`
- `src/test/java/vista/ExtensionMarketplaceDependencyTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -Dtest=ExtensionMarketplaceDependencyTest test`
- `mvn -q -DskipTests compile`
- `mvn -q test`

## Detailed Process

- `docs/fixes/process/extensions-dependency-order-warning.md`

## Regression Notes

Marketplace dependency resolution should help users add dependencies, but it should not prevent downloading the selected plugin. Keep hard validation for incompatible or unavailable download plans, not for dependency order.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
