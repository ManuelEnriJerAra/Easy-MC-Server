# Extensions Marketplace Warning Results Hidden

## Status

Fixed

## Original Issue

The mod marketplace could appear empty for a Fabric `26.1.2` server even though Modrinth returned matching mod projects.

## Root Cause

Modrinth search rows were discarding the platform/version evidence that came from the active search facets. Dora then marked those rows as metadata-pending `WARNING`, and the default marketplace list only kept rows marked `COMPATIBLE`, so every provider-filtered Modrinth row could be hidden before the user had a chance to open details.

## Solution

The default marketplace result filter now hides only clearly incompatible rows. Modrinth filtered search rows also preserve the query-matched loader and Minecraft version, so Fabric `26.1.2` results can render as compatible instead of `Revisar`, `Por confirmar`, or a blank compatibility state. Typed searches still show incompatible rows for user review, matching the existing exploratory search behavior.

## Files Changed

- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `src/main/java/controlador/extensions/ModrinthExtensionCatalogProvider.java`
- `src/test/java/controlador/extensions/ModrinthExtensionCatalogProviderTest.java`
- `src/test/java/vista/ExtensionMarketplaceInteractionTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -Dtest=ExtensionMarketplaceInteractionTest test`
- `mvn -q -Dtest=ModrinthExtensionCatalogProviderTest#shouldResolveDownloadForModernNumericMinecraftVersion test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/extensions-marketplace-warning-results-hidden.md`

## Regression Notes

Do not require search rows to be fully `COMPATIBLE` before details are loaded. Provider-side search facets and service safety filters already narrow default rows; the provider should retain query-matched compatibility evidence, and the UI should reserve strict compatibility blocking for rows that are explicitly incompatible.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
