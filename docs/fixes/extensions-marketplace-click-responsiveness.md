# Extension Marketplace Click Responsiveness

## Status

Fixed

## Original Issue

Marketplace result and queue actions only worked reliably when the mouse did not move between press and release. Normal small hand movement could make an intended click feel ignored.

## Root Cause

The marketplace used `mouseClicked` for row actions. Swing does not fire `mouseClicked` for small drag-like movement during a press/release gesture, so action icons required an overly exact click.

## Solution

Result-row and queue-row actions now use press/release handling with a small movement tolerance. The result-row action hit zone is also wider so the visible action icon is easier to activate.

## Files Changed

- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `src/test/java/vista/ExtensionMarketplaceInteractionTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -Dtest=ExtensionMarketplaceInteractionTest test`
- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=ExtensionMarketplaceInteractionTest,ExtensionMarketplaceDependencyTest" test`
- `mvn -q test`

## Detailed Process

- `docs/fixes/process/extensions-marketplace-click-responsiveness.md`

## Regression Notes

Do not rely on exact `mouseClicked` events for row-level action zones. Use press/release state with a bounded movement tolerance so normal clicks remain responsive without turning drags into actions.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
