# Extensions Mods Missing From Catalog

## Status

Fixed

## Original Issue

Mods were not showing in the extension marketplace catalog for some mod-capable servers. The catalog should show compatible mod results for Forge, NeoForge, Fabric, and Quilt servers while keeping plugin-only servers on plugin results.

## Root Cause

`PanelExtensiones` could resolve a missing or `UNKNOWN` server ecosystem from the known server platform, but `ExtensionMarketplaceDialog` read `server.getEcosystemType()` directly in provider filtering, platform filter setup, compatibility checks, and title/type resolution. Servers with a known mod platform but stale `UNKNOWN` ecosystem metadata could therefore open the marketplace without a mod ecosystem or mod extension type.

## Solution

`ExtensionMarketplaceDialog` now resolves marketplace ecosystem/type through a shared fallback helper: explicit non-unknown ecosystem first, otherwise the platform's default ecosystem. The provider selector, loader/platform filter, catalog title, compatibility cache key, and compatibility assessment now use that resolved ecosystem.

## Files Changed

- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `src/test/java/vista/ExtensionMarketplaceInteractionTest.java`
- `docs/fixes/process/extensions-mods-missing-from-catalog.md`

## Verification

- `mvn -q "-Dtest=ExtensionMarketplaceInteractionTest,PanelExtensionesTest" test`
- `mvn -q -DskipTests compile`

Manual marketplace interaction was not run in this terminal-only pass.

## Detailed Process

- `docs/fixes/process/extensions-mods-missing-from-catalog.md`

## Regression Notes

If mod results disappear again, check whether the dialog and panel still use the same ecosystem fallback for servers whose persisted metadata is older than the platform model. Provider descriptors should also keep non-searchable stub providers hidden from marketplace search.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
