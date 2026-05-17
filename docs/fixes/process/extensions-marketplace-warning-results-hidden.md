# Extensions Marketplace Warning Results Hidden Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-marketplace-warning-results-hidden.md`

## Scope

Fix the marketplace list appearing empty for mod servers when provider search rows are compatible by query but still marked `WARNING` because detailed compatibility metadata has not been loaded yet. Keep incompatible rows hidden from the default list, and preserve typed-search behavior.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md`, the extensions pipeline, and the Modrinth provider/search UI path. |
| DONE | 2. Implement the fix | Adjusted the marketplace display filter, preserved query-matched Modrinth compatibility metadata, and added provider/UI regression coverage. |
| DONE | 3. Verify behavior | Ran targeted marketplace/provider tests and compile. |

## Implementation Notes

Modrinth search rows previously kept compatibility platform/version sets empty until details were loaded, so the UI labeled them as `WARNING`. The default search worker then required `COMPATIBLE` for non-typed searches, causing otherwise valid provider-filtered rows to disappear.

Live Modrinth search for Fabric `26.1.2` still returns thousands of mod project hits, so the empty UI was caused by Dora's local post-filter rather than the upstream catalog.

Follow-up UI review showed that keeping those rows as normal warning badges made every mod say `Revisar` even though the status did not identify a real issue. A second review showed that neutral or blank compatibility states were still confusing when applied to every row. Modrinth filtered search rows now retain the active query's matched loader and Minecraft version, so server-filtered Fabric `26.1.2` rows can show normal compatibility while details and install resolution still verify the exact downloadable build.

## Verification Notes

- `mvn -q -Dtest=ExtensionMarketplaceInteractionTest test` passed.
- `mvn -q -Dtest=ModrinthExtensionCatalogProviderTest test` passed.
- `mvn -q -Dtest=ModrinthExtensionCatalogProviderTest#shouldResolveDownloadForModernNumericMinecraftVersion test` passed.
- `mvn -q -DskipTests compile` passed.
