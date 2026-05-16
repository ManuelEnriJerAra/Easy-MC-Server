# Modrinth Broad Search Download Resolution

## Status

Fixed

## Original Issue

Typed Modrinth marketplace searches could show many mods as compatible, then fail with `No se ha podido preparar una descarga compatible.` when Dora tried to resolve a download for the selected server.

## Root Cause

Typed searches intentionally skipped loader and Minecraft version facets for broader discovery. Dora then evaluated Modrinth search-result project metadata as if it proved a concrete server-compatible file existed. A project can list a loader and a Minecraft version somewhere in its lifetime metadata while having no downloadable version that combines both for the active server.

The quick-add path also passed the search row's latest version id into download resolution, even though that id may not be compatible with the selected server after version-level filtering.

Nova by xenondevs exposed a second variant: current Modrinth Nova builds use Minecraft versions such as `26.1.1` and `26.1.2`. Dora's shared Minecraft version parser only recognized `1.xx`, so provider download resolution and service-level install checks could treat those versions as unknown.

A later review found that Modrinth's `latest_version` field on search hits is project-global, not server-filtered. For Nova, a search row for an older Minecraft server can still carry the newest `26.1.2` version id. The dialog also cached compatibility without including the metadata sets that produced the assessment, so broad search rows and resolved details could reuse stale compatibility state.

## Solution

`ModrinthExtensionCatalogProvider` now leaves platform and Minecraft compatibility metadata empty for Modrinth search rows, causing the UI to treat those rows as needing review instead of compatible until version-level data is loaded.

Modrinth search rows also no longer expose `latest_version` as an installable version id. They remain project-discovery rows until details or download resolution returns a concrete server-compatible build.

`ExtensionMarketplaceDialog` now loads details with the active server platform and Minecraft version, so the version selector is populated from actual compatible Modrinth builds. Quick-add from the search row resolves the best compatible build without forcing the row's latest version id.

Modrinth details still load with active server filters through `/project/{id}/version`, so the version selector and preview are based on concrete builds. Download resolution now treats a requested version id as exact. If that specific version is not available after server filtering, it returns no plan instead of silently selecting another build.

The shared extension-version parser now recognizes current numeric Minecraft versions such as `26.1.2`, so Nova resolves and validates against the actual selected server version instead of falling through to the newest remote build.

The marketplace dialog also no longer queues unresolved rows directly. If a search result is only marked `Revisar`, the row action opens the details/preview path first. If server-filtered details return no versions, the detail panel shows a blocked no-build state instead of creating a fallback install candidate from the search result.

A follow-up review found that the queue button could still become clickable because the label/icon refresh re-enabled it after the availability check disabled it. The label refresh now preserves disabled availability unless the action is intentionally remove/uninstall. Catalog download safety also now infers the ecosystem from a known platform when stored ecosystem metadata is unknown.

Queue preparation items created from unresolved search rows now carry no version id until the provider returns a real download plan. Dialog download-plan cache keys include the active server platform, loader, and version, while compatibility cache keys include the entry metadata used for assessment.

## Files Changed

- `src/main/java/controlador/extensions/ModrinthExtensionCatalogProvider.java`
- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `src/test/java/controlador/extensions/ModrinthExtensionCatalogProviderTest.java`
- `src/test/java/vista/ExtensionMarketplaceDependencyTest.java`
- `docs/fixes/process/extensions-modrinth-broad-search-download-resolution.md`

## Verification

- `mvn -q -Dtest=ModrinthExtensionCatalogProviderTest test`
- `mvn -q -Dtest=ServerExtensionsServiceTest test`
- `mvn -q "-Dtest=ModrinthExtensionCatalogProviderTest,ServerExtensionsServiceTest" test`
- `mvn -q "-Dtest=ModrinthExtensionCatalogProviderTest,ServerExtensionsServiceTest,ExtensionCatalogServiceTest" test`
- `mvn -q "-Dtest=ModrinthExtensionCatalogProviderTest,ExtensionMarketplaceDependencyTest,ExtensionCatalogServiceTest,ServerExtensionsServiceTest" test`
- `mvn -q -DskipTests compile`

All passed after the TaCZ/Nova, Nova/xenondevs, action-state, and catalog-safety follow-ups. Maven emitted the expected Lombok/Guice `sun.misc.Unsafe` warning; the catalog-service run also logs expected warnings from existing broken-provider tests.

## Detailed Process

- `docs/fixes/process/extensions-modrinth-broad-search-download-resolution.md`

## Regression Notes

Keep project-level search metadata separate from version-level installability. Even filtered Modrinth search facets can match loader and Minecraft version independently across a project's history. Search results can help users discover projects, but the compatible badge and install plan should only become confident once loader, Minecraft version, and downloadable file are resolved together. Keep Minecraft version parsing current with Modrinth/Mojang version schemes; unknown versions silently weaken both download filtering and install checks.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
