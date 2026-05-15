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
- `controlador.extensions.ModrinthModpackService`
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
- `vista.ProcessWizardDialog`

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

Do not let extension display metadata keep live handles to installed jars. Embedded icon references may use `jar:file:` URLs, but icon loading must disable URL caches and eagerly decode the image into memory before UI rendering so users can delete or rename stopped-server jars while the app is open.

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
2. Search the selected provider through `ExtensionCatalogService`.
3. Load details/versions.
4. Evaluate compatibility and dependencies.
5. Build `ExtensionDownloadPlan`.
6. Queue or install downloads.
7. Persist installed metadata/cache.

Providers:

- Modrinth
- Hangar
- CurseForge currently has stub/provider support plus modpack service behavior.

Marketplace search is provider-scoped from the UI. The provider selector does not offer an "all providers" mode because cross-provider aggregation can show the same plugin twice and can mix dependency identities from different catalogs. Hangar is the default provider when available because its plugin dependency metadata is usually better; users can switch to Modrinth for its broader catalog.

## Dependency Resolution

Dependency prompts and queue logic live mostly in `ExtensionMarketplaceDialog`, while installation compatibility is enforced by `ServerExtensionsService`.

Keep UI dependency decisions and service-level validation consistent. Service validation is the safety net.

## Modpack Import/Export

`ModrinthModpackService` is the practical modpack path for mod servers. It writes and reads Modrinth `.mrpack` archives with `modrinth.index.json`, validates ZIP/index paths defensively, verifies downloads against indexed hashes, installs indexed jars through `ServerExtensionsService`, and extracts path-validated overrides. Current import override policy still permits top-level `mods/*.jar` overrides and is tracked in `docs/pending-fixes/extensions-modrinth-import-mod-jar-overrides.md`. Modrinth-origin installed mods are exported as indexed files when their persisted `projectId` and `versionId` resolve and local hashes match. Manual/local installed jars may be exported as indexed Modrinth files only when Modrinth `/version_file/{hash}` resolves an exact version file and the returned hashes match the local jar; the recovered Modrinth source metadata should be persisted afterward. Non-Modrinth/local jars that cannot be resolved this way must be skipped instead of exported as overrides. Hashes should be calculated once per local jar during an export and reused for all SHA-1/SHA-512 checks.

Modrinth import must ask the user which content side to install: server, client, or complete, defaulting the selection to complete. Do not silently infer this choice. `PanelExtensiones` shows a modal loading dialog while the import worker downloads, verifies, installs, and extracts selected content. Loading dialogs should use the shared `AppTheme.createLoadingProgressBar(...)` style so progress bars remain visually consistent across import/download flows.

When importing a modpack into a server that already has managed mods, the UI must ask how to handle the current mods. Keep mode skips already installed/conflicting entries with warnings. Replace mode is destructive: after the pack metadata and planned downloads are resolved, delete only non-symlink regular `.jar` files inside adapter-managed extension directories under the server folder, refresh extension state, then install the modpack entries. Do not delete unmanaged files, nested folders, symlinks, or files outside the server directory.

Modpack import uses `ProcessWizardDialog` so content selection, existing-mod handling, and final review happen in one guided process. When existing mods are detected, keep existing-mod handling below modpack content in the same step. Reuse pre-read pack metadata and already-synchronized extension state when starting the import worker; avoid parsing the same pack or rescanning extension directories again after the wizard.

`CurseForgeModpackService` remains as legacy support for CurseForge-style `manifest.json` ZIPs. CurseForge import still depends on a configured CurseForge API key to resolve manifest file IDs.

Be careful about side classification, optional files, and skipped entries. Do not silently include client-only files in server exports or server imports.

## Tests

Relevant tests:

- `ServerExtensionsServiceTest`
- `ModrinthModpackServiceTest`
- `GestorServidoresTest`
- `ExtensionCatalogServiceTest`
- `ModrinthExtensionCatalogProviderTest`
- `HangarExtensionCatalogProviderTest`
- `ExtensionMarketplaceDependencyTest`
- `PanelExtensionesTest`
- `ExtensionDescriptionRendererTest`

Run targeted tests when changing parsing, compatibility, dependency resolution, catalog behavior, or install/remove flows.
