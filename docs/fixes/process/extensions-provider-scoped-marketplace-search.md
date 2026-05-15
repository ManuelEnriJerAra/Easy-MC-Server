# Provider-Scoped Marketplace Search Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-provider-scoped-marketplace-search.md`

## Scope

Remove all-provider marketplace aggregation so plugin results and dependency resolution stay within one selected catalog provider. Default to Hangar when available because it currently provides better plugin dependency metadata.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect marketplace provider flow | Reviewed `ExtensionMarketplaceDialog`, `ExtensionCatalogService`, and dependency resolution keys. |
| DONE | 2. Identify duplicate source | Found that the UI added `Todos los proveedores`, which built search queries with null provider id and aggregated Modrinth plus Hangar. |
| DONE | 3. Scope search to one provider | Removed the all-provider option and made the provider combo select Hangar first when it is available. |
| DONE | 4. Document catalog behavior | Updated the extension pipeline and added the solved fix note. |
| DONE | 5. Verify behavior | Compiled after the change. |

## Implementation Notes

Modrinth and Hangar can both publish the same plugin, but their project ids and dependency ids are provider-specific. Without a canonical cross-provider identity model, merging providers in one search result set creates duplicate rows and can make dependencies look missing or unrelated.

The conservative fix is to keep each marketplace search provider-scoped. Users can switch providers explicitly, and dependency resolution remains aligned with the selected provider's ids.

## Verification Notes

- `mvn -q -DskipTests compile` passed.
- Maven emitted the expected Lombok/Guice `sun.misc.Unsafe` warning.
