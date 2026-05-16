# Extension Marketplace Dialog Has Too Many Responsibilities

## Status

Fixed

## Original Issue

`ExtensionMarketplaceDialog` mixed orchestration code with a large cluster of view-state records, filter options, dependency models, queue models, and helper enums, which made the dialog harder to scan and review.

## Root Cause

Many behavior-neutral support types were nested directly inside the dialog instead of living as package-scoped helpers, so the dialog file absorbed structural weight that did not need to stay in the class body.

## Solution

Extracted the dialog's helper records, enums, and queue/dependency model types into `vista.ExtensionMarketplaceDialogModels`. The dialog now keeps the same behavior while relying on package-scoped support types defined alongside it.

## Files Changed

- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `src/main/java/vista/ExtensionMarketplaceDialogModels.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -DskipTests compile` not run here because `mvn` is unavailable in this shell.
- Targeted follow-up: run `mvn test -Dtest=ExtensionMarketplaceDependencyTest` and smoke-test marketplace search/details/queue flows.

## Detailed Process

- `docs/fixes/process/extensions-large-dialog-responsibilities.md`

## Regression Notes

If the dialog grows again, extract behavior-neutral state and helper types first before moving worker/control flow. Keep extracted helper files package-scoped unless another UI class genuinely needs a public API.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
