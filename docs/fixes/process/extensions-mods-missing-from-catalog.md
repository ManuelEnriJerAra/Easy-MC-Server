# Extensions Mods Missing From Catalog Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-mods-missing-from-catalog.md`

## Scope

Resolve the pending issue where mod-capable servers could open the marketplace without mod catalog results. This covers the marketplace ecosystem/type resolution path and keeps plugin catalog behavior unchanged.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Reviewed the pending fix plus `docs/README.md`, `extensions-pipeline.md`, `models-and-data-pipeline.md`, and `filesystem-and-paths-pipeline.md`. |
| DONE | 2. Trace catalog/provider flow | Checked `PanelExtensiones`, `ExtensionMarketplaceDialog`, provider descriptors, and Modrinth/CurseForge behavior. CurseForge is already hidden from real search because it exposes no search capability. |
| DONE | 3. Implement the fix | Centralized marketplace ecosystem fallback so an `UNKNOWN` server ecosystem can still resolve from a known Forge/Fabric/NeoForge/Quilt or plugin platform. |
| DONE | 4. Verify behavior | Added a marketplace regression test, ran focused UI tests, and ran the required compile check. |

## Implementation Notes

`PanelExtensiones` already resolves a server ecosystem from the platform when persisted ecosystem metadata is missing or `UNKNOWN`. `ExtensionMarketplaceDialog` had several direct reads of `server.getEcosystemType()`, so a mod platform with stale `UNKNOWN` ecosystem metadata could open the marketplace with an unknown extension type and no mod provider/filter context.

The fix keeps the service/provider contracts intact and makes the dialog follow the same fallback rule as the panel: explicit non-unknown ecosystem wins, otherwise the platform default ecosystem is used.

## Verification Notes

- `mvn -q "-Dtest=ExtensionMarketplaceInteractionTest,PanelExtensionesTest" test` passed.
- `mvn -q -DskipTests compile` passed.
- Manual catalog interaction was not run in this terminal-only pass.
