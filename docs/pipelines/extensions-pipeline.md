# Extensions Pipeline

Use this guide before editing mods/plugins listing, manual install, catalog search, dependency resolution, updates, modpack import/export, or extension metadata parsing.

## Core Packages

Controller services:

- `controlador.extensions.ServerExtensionsService`
- `controlador.extensions.ExtensionCatalogService`
- `controlador.extensions.ExtensionCatalogRegistry`
- `controlador.extensions.ModrinthExtensionCatalogProvider`
- `controlador.extensions.HangarExtensionCatalogProvider`
- `controlador.extensions.CurseForgeModpackService`
- `controlador.extensions.InstalledExtensionsCacheService`
- `controlador.extensions.ExtensionArtifactDownloader`

UI:

- `vista.PanelExtensiones`
- `vista.ExtensionMarketplaceDialog`
- `vista.ExtensionDescriptionRenderer`
- `vista.ExtensionDetailsLayout`
- `vista.ExtensionIconLoader`
- `vista.ExtensionStatusPresentation`
- `vista.MarketplaceEntryViewModel`

Models:

- `modelo.extensions.*`

## Managed Directories

Extension directories come from the current server platform adapter.

Typical locations:

- plugins for Bukkit/Paper/Purpur/Pufferfish/Spigot style servers
- mods for Forge/NeoForge/Fabric/Quilt style servers

Do not assume a hard-coded directory without checking platform capabilities.

## Installed Extension Detection

`ServerExtensionsService.detectInstalledExtensions(...)` scans managed directories and reads jar metadata.

Metadata sources include:

- `plugin.yml`
- `paper-plugin.yml`
- `fabric.mod.json`
- `quilt.mod.json`
- `META-INF/mods.toml`
- manifests and inferred metadata

The service merges detected metadata with persisted cache data so catalog origin/update data survives rescans.

## Manual Install Pipeline

1. User selects a jar in `PanelExtensiones`.
2. `ServerExtensionsService.validateCompatibility(...)` checks ecosystem/platform/version.
3. `installManualJar(...)` copies the jar into the managed extension directory.
4. Metadata/cache is updated.
5. UI refreshes installed extensions.

Avoid installing plugins into mod directories or mods into plugin directories unless compatibility rules explicitly allow it.

## Catalog Pipeline

`ExtensionMarketplaceDialog` performs catalog search and install.

Flow:

1. Build `ExtensionCatalogQuery`.
2. Search providers through `ExtensionCatalogService`.
3. Load details/versions.
4. Evaluate compatibility and dependencies.
5. Build `ExtensionDownloadPlan`.
6. Queue or install downloads.
7. Persist installed metadata/cache.

Providers:

- Modrinth
- Hangar
- CurseForge currently has stub/provider support plus modpack service behavior.

## Dependency Resolution

Dependency prompts and queue logic live mostly in `ExtensionMarketplaceDialog`, while installation compatibility is enforced by `ServerExtensionsService`.

Keep UI dependency decisions and service-level validation consistent. Service validation is the safety net.

## Modpack Import/Export

`CurseForgeModpackService` handles CurseForge-style pack manifests and export modes.

Be careful about side classification and skipped entries. Do not silently include client-only files in server exports.

## Tests

Relevant tests:

- `ServerExtensionsServiceTest`
- `ExtensionCatalogServiceTest`
- `ModrinthExtensionCatalogProviderTest`
- `HangarExtensionCatalogProviderTest`
- `ExtensionMarketplaceDependencyTest`
- `PanelExtensionesTest`
- `ExtensionDescriptionRendererTest`

Run targeted tests when changing parsing, compatibility, dependency resolution, catalog behavior, or install/remove flows.
