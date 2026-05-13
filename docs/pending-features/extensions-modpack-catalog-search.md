# Modpack Catalog Search

## Status

Pending

## Area

`vista.ExtensionMarketplaceDialog`, `vista.PanelExtensiones`, `controlador.extensions`, Modrinth catalog provider, modpack import/install flow, extension filesystem handling.

## Feature Request

Add modpack searching to the catalog/marketplace.

This must be exclusive to mod servers only. Plugin servers should not show modpack search or modpack install actions.

The modpack catalog should behave like users expect from a modpack browser: searchable results, detailed descriptions, images/screenshots, supported Minecraft versions/loaders, downloadable versions, and an install/import flow. If the selected server already has installed mods, the app should ask whether the user wants to keep the existing mods and add the modpack content on top, or delete current managed mods and import the selected modpack.

## Motivation

Searching individual mods is useful, but users often want to install a whole curated pack. A modpack catalog makes Easy MC Server a more complete modded-server manager and pairs naturally with the requested Modrinth `.mrpack` import/export support.

## Desired Behavior

Modpack search should be available only when the selected server is a mods ecosystem server, such as Forge, NeoForge, Fabric, or Quilt.

The catalog should provide a clear mode for searching modpacks, separate from searching individual mods:

- Search by text, version, loader, and sort order.
- Query Modrinth modpack projects, not normal mod projects.
- Show modpack results with icon, title, author, short description, downloads/follows if available, supported loaders, and supported Minecraft versions.
- Show a detail pane with long description/body content, project links, license, categories, available versions, and gallery images/screenshots.
- Load images through the existing async image/icon infrastructure where possible.
- Preserve the current marketplace feel, but make it clear the selected item is a modpack, not a single mod.

Installing a modpack from the catalog should:

- Require a mod server and reject plugin/unknown ecosystems with clear Spanish copy.
- Resolve a compatible Modrinth modpack version for the selected server version/loader.
- Download the selected Modrinth pack file, normally a `.mrpack`.
- Reuse the future Modrinth modpack import flow to validate, hash-check, download indexed files, apply allowed overrides, and install mods.
- Before installing, detect whether the current server already has mods in managed mod directories.
- If mods already exist, ask the user whether to:
  - keep current mods and install the modpack alongside them;
  - delete current managed mods and import the modpack contents;
  - cancel.
- If replacing, delete only managed `.jar` files in managed extension directories after preflight validation. Do not delete unmanaged files, symlinks, nested folders, or files outside the server directory.
- If keeping, warn about possible incompatibilities or duplicate mods and rely on compatibility checks where possible.
- Refresh installed extensions and persisted metadata after install.

## Notes

- Current marketplace flow lives mostly in `ExtensionMarketplaceDialog`.
- Current marketplace search builds `ExtensionCatalogQuery` and routes through `ExtensionCatalogService`.
- Current Modrinth provider already searches `/search`, loads project details, lists project versions, and resolves download plans for individual mods/plugins.
- Current `ExtensionCatalogQuery` only models `ServerExtensionType.MOD`, `PLUGIN`, and `UNKNOWN`; there is no modpack content type yet.
- Current `ExtensionCatalogDetails` supports summary, links, license, categories, and versions, but does not model a gallery/image list.
- Current `ModrinthExtensionCatalogProvider` filters project type using `project_type:mod` or plugin-related facets. Modpack search will need `project_type:modpack`.
- Current `PanelExtensiones` has modpack import/export actions, but the existing import path is CurseForge-shaped. This feature should align with the pending Modrinth `.mrpack` import/export feature.
- Official Modrinth search docs document `project_type` facets, including `project_type:modpack`, and project details include long `body` text and gallery image data.

## Suggested Approach

Add a catalog-level distinction between installable extension projects and modpack projects.

Possible implementation path:

- Introduce a catalog content type or separate query/model records for modpacks, instead of forcing modpacks into `ServerExtensionType.MOD`.
- Extend or add Modrinth provider methods for searching `project_type:modpack`, fetching details, listing modpack versions, and resolving the selected version's `.mrpack` file.
- Add gallery/image fields to the detail model used by the UI, or create a modpack-specific details record.
- Add a modpack mode/tab/toggle in `ExtensionMarketplaceDialog` that is visible only for mod servers.
- Keep individual mod search as the default for mod servers unless UX decides otherwise.
- Use the implemented `ModrinthModpackService` documented in `docs/features/extensions-modrinth-modpack-import-export.md` to install the selected pack file.
- Add an install preflight step that inspects existing managed mod directories and prompts for keep/replace/cancel.
- Implement replace mode with the same scoped deletion rules as direct modpack import.
- Keep all user-facing copy Spanish and clear that replace deletes current managed mod jars.

This feature can build on the completed Modrinth modpack import/export work.

## Verification

- Run `mvn -q -DskipTests compile`.
- Add provider tests for Modrinth modpack search using `project_type:modpack`, loader facets, Minecraft version facets, sorting, and pagination.
- Add details tests for long description/body and gallery image parsing.
- Add UI/model tests where practical for modpack mode being visible for mod servers and hidden for plugin/unknown servers.
- Add install-flow tests for:
  - no existing mods;
  - existing mods with keep selected;
  - existing mods with replace selected;
  - cancel selected;
  - incompatible Minecraft version/loader;
  - failed `.mrpack` download or invalid pack file.
- Manually search Modrinth modpacks from a Fabric/Forge/NeoForge/Quilt server and verify results, descriptions, images, versions, and install prompts.
- Manually verify plugin servers do not expose modpack catalog search.
- Manually verify replacing mods deletes only managed `.jar` files and then installs the selected pack.

## Related Docs

- `docs/pending-features/extensions-modrinth-modpack-import-export.md`
- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `https://docs.modrinth.com/api/operations/searchprojects/`
- `https://docs.modrinth.com/api/operations/getproject/`
