# Geyser Modrinth Plugin Search

## Status

Fixed

## Original Issue

Geyser could fail in the marketplace on plugin servers with `No se ha podido preparar una descarga compatible.`

## Root Cause

Modrinth currently reports Geyser as `project_type: mod` while also listing plugin loaders such as Paper and Spigot. The Modrinth provider inferred the extension type from the declared project type unless the platform set was plugin-only. Since Geyser is multi-loader, its mixed platform set was considered ambiguous.

The queue path could also request a specific search-result version id. At the time, the queue path depended on provider-side fallback when that id was not present after narrowing versions by the active server loader and Minecraft version.

## Solution

`ModrinthExtensionCatalogProvider` now uses the requested server platform and ecosystem when a result has mixed loaders. If a plugin search for a Paper-compatible server receives a multi-loader project that explicitly includes Paper, the entry is kept as a plugin result.

The queue path now resolves compatible builds without forcing the broad search row's latest version id; exact version selections remain exact.

## Files Changed

- `src/main/java/controlador/extensions/ModrinthExtensionCatalogProvider.java`
- `src/test/java/controlador/extensions/ModrinthExtensionCatalogProviderTest.java`
- `docs/fixes/process/extensions-geyser-modrinth-plugin-search.md`

## Verification

- `mvn -q -Dtest=ModrinthExtensionCatalogProviderTest test`
- `mvn -q -DskipTests compile`

Both passed. Maven emitted the expected Lombok/Guice `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/fixes/process/extensions-geyser-modrinth-plugin-search.md`

## Regression Notes

Marketplace provider classification should account for multi-loader projects whose declared Modrinth project type does not match the active server ecosystem. Keep installation-time compatibility checks strict, and avoid relying on broad search-row version ids as exact install targets.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
