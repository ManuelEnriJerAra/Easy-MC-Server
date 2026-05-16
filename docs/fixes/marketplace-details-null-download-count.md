# Marketplace Details Null Download Count

## Status

Fixed

## Original Issue

Loading marketplace details for an installed extension could log a warning and fail to persist enriched metadata when the installed local metadata had `downloadCount == null`.

## Root Cause

`ExtensionMarketplaceDialog.persistCatalogDetailsOnInstalledExtension(...)` compared `metadata.getDownloadCount()` directly with a primitive catalog download count. The nullable `Long` was unboxed, causing a `NullPointerException`.

## Solution

The metadata persistence path now stores the current download count in a `Long` and explicitly handles null before comparing with the catalog value.

## Files Changed

- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `src/test/java/vista/ExtensionMarketplaceDependencyTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -Dtest=ExtensionMarketplaceDependencyTest test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/marketplace-details-null-download-count.md`

## Regression Notes

Treat installed extension metadata as partially populated. Boxed values from `ExtensionLocalMetadata` should be null-checked before comparison or unboxing.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
