# Hangar Dependency Name Resolution

## Status

Fixed

## Original Issue

Resolving a Hangar dependency could log `Fallo al resolver descarga desde hangar` when the dependency identifier was `NBT+API` or `PacketEvents`. Hangar returned HTTP 404 for direct project URLs such as `https://hangar.papermc.io/api/v1/projects/NBT+API` and `https://hangar.papermc.io/api/v1/projects/PacketEvents`.

## Root Cause

`HangarExtensionCatalogProvider.readProject(...)` assumed every dependency `projectId` was directly valid for `/api/v1/projects/{project}`. Hangar dependency metadata can provide display-style identifiers that differ from the canonical numeric id or namespace slug used by project/version endpoints. Some dependency names can also refer to external/non-Hangar projects; searching Hangar for those names may return related addons but no exact project.

## Solution

Hangar project lookup now uses a provider-scoped project search before direct lookup for non-numeric simple identifiers and after direct lookup failures otherwise. The fallback accepts only exact normalized matches against the result id, name, namespace slug, or owner/slug, then continues version/download resolution through the resolved canonical project node.

If search completes without an exact match, the provider returns no download plan instead of throwing a 404-backed exception. This lets dependency resolution treat the item as unresolved without logging a provider failure.

Download plans resolved this way store the canonical Hangar project id from the resolved project.

## Files Changed

- `src/main/java/controlador/extensions/HangarExtensionCatalogProvider.java`
- `src/test/java/controlador/extensions/HangarExtensionCatalogProviderTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.extensions.HangarExtensionCatalogProviderTest" test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/extensions-hangar-dependency-name-resolution.md`

## Regression Notes

If Hangar dependency resolution starts warning on 404s again, check whether dependency metadata is using a display name or punctuation variant rather than a canonical Hangar id/slug, or whether it is an external dependency that should remain unresolved. The search fallback should remain exact-normalized so broad marketplace search results do not accidentally satisfy unrelated dependencies.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
