# Hangar Direct External Download Resolution

## Status

Fixed

## Original Issue

Nova from Hangar appeared compatible for a Paper `26.1.2` server, but Dora could not prepare a compatible download.

## Root Cause

Hangar exposes Nova `0.23.0` for Paper `26.1.2` as an external download. The API returns no Hangar CDN `downloadUrl` and no `fileInfo`; instead `downloads.PAPER.externalUrl` points directly to a GitHub release `.jar` asset.

Dora's Hangar provider only resolved external URLs when they were Modrinth project pages. Generic external websites were intentionally blocked, but direct `.jar` asset URLs were also blocked by accident.

## Solution

`HangarExtensionCatalogProvider` now accepts direct `http`/`https` external URLs whose path ends in `.jar`, derives the file name from the URL path, and still rejects generic external websites that do not identify a downloadable jar.

## Files Changed

- `src/main/java/controlador/extensions/HangarExtensionCatalogProvider.java`
- `src/test/java/controlador/extensions/HangarExtensionCatalogProviderTest.java`
- `docs/fixes/process/extensions-hangar-direct-external-download-resolution.md`

## Verification

- `mvn -q -Dtest=HangarExtensionCatalogProviderTest test`

## Detailed Process

- `docs/fixes/process/extensions-hangar-direct-external-download-resolution.md`

## Regression Notes

Keep the distinction between external websites and external assets. Hangar plugins can use `externalUrl` for direct GitHub release jars; those should be installable. Plain project pages, documentation pages, or other non-jar external URLs should remain blocked unless a provider-specific resolver can derive a concrete jar download.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
