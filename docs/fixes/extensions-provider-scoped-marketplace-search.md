# Provider-Scoped Marketplace Search

## Status

Fixed

## Original Issue

After Geyser became visible from Modrinth for plugin servers, the marketplace could show duplicate plugin rows from Modrinth and Hangar. Dependencies could then be evaluated against the wrong catalog identity because providers do not share stable project ids.

## Root Cause

The marketplace offered an all-provider search mode. `ExtensionCatalogQuery` used a null provider id for that mode, so `ExtensionCatalogService` aggregated every compatible provider. Search result deduplication only removed duplicates inside the same provider, and dependency matching remained provider-scoped.

## Solution

Marketplace search now requires a concrete provider selection. The provider combo no longer includes an all-provider option, and Hangar is selected first when available because it currently provides better plugin dependency metadata. Users can still switch to Modrinth when they want its broader catalog.

## Files Changed

- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `docs/pipelines/extensions-pipeline.md`
- `docs/fixes/process/extensions-provider-scoped-marketplace-search.md`

## Verification

- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/extensions-provider-scoped-marketplace-search.md`

## Regression Notes

Provider-scoped marketplace searches avoid cross-provider duplicates and keep dependency ids consistent with the selected catalog. Do not reintroduce all-provider aggregation without adding canonical cross-provider identity and dependency matching first.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
