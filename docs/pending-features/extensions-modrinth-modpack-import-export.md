# Modrinth Modpack Import And Export

## Status

Pending

## Area

`controlador.extensions`, `controlador.GestorServidores`, `vista.PanelExtensiones`, extension metadata/cache, and modpack import/export filesystem handling.

## Feature Request

Add Modrinth modpack import/export support and adapt the current modpack workflow around it.

The app currently supports CurseForge-style modpack export as a `manifest.json` ZIP, and has a CurseForge manifest import path. That import path requires a CurseForge API key to resolve manifest file IDs, so it is not useful when the user does not have API access. Do not delete the CurseForge logic, but add a new Modrinth import/export path and make the practical modpack workflow use Modrinth.

## Motivation

Modrinth is already supported by the extension marketplace and does not require the same private API-key setup for ordinary project/version metadata and downloads. Modrinth `.mrpack` import/export would make modpack sharing usable without requiring CurseForge credentials.

## Desired Behavior

The app should support exporting the selected mod server as a Modrinth `.mrpack` archive:

- Write a ZIP archive with `.mrpack` extension.
- Include `modrinth.index.json` in the archive root.
- Include pack metadata such as pack name, summary/version as available, format version, Minecraft dependency, and loader dependency.
- Include installed Modrinth-origin mods as indexed files with download URLs and hashes, using each mod's known Modrinth project/version identity.
- Preserve the existing export mode idea (`Servidor`, `Cliente`, `Completo`) where it makes sense, including side filtering so client-only files are not silently included in server exports.
- Include config/override files only when the export mode allows them and the app can classify them safely.
- Report skipped entries clearly, especially local/manual/CurseForge/Hangar files that cannot be represented as Modrinth indexed downloads.

The app should support importing a Modrinth `.mrpack` archive into the selected mod server:

- Accept `.mrpack` files in the import chooser.
- Read and validate `modrinth.index.json`.
- Warn when the pack Minecraft version or loader dependencies do not match the selected server.
- Download required indexed files from their listed URLs.
- Verify downloaded files against `hashes`, preferring SHA-512 when available.
- Install compatible mods into the selected server's managed extension directory through `ServerExtensionsService`, preserving metadata/cache as Modrinth-origin entries.
- Extract allowed override content into the server directory only after validating paths defensively.
- Surface optional files, failed downloads, hash mismatches, unsupported loaders, and skipped client-only content as Spanish warnings/errors.

The existing CurseForge import/export code should remain available for future use, but the UI should not present CurseForge as the only modpack path when it cannot work without an API key.

## Notes

- Current UI entry points are `PanelExtensiones.exportarModpack()` and `PanelExtensiones.importarModpack()`.
- Current workers are `PanelExtensiones.ExportModpackWorker` and `PanelExtensiones.ImportModpackWorker`, both typed to `CurseForgeModpackService`.
- Current controller wrappers are `GestorServidores.exportarModpackCurseForge(...)`, `leerManifestModpackCurseForge(...)`, and `importarModpackCurseForge(...)`.
- Current service is `CurseForgeModpackService`, which writes `manifest.json`, creates an `overrides/` directory, and resolves imports through CurseForge API endpoints.
- `CurseForgeModpackService.resolveDownloadPlan(...)` explicitly fails with "Se necesita una API key de CurseForge..." when no key is configured.
- Modrinth marketplace support already exists through `ModrinthExtensionCatalogProvider`, `ExtensionCatalogService`, `ExtensionDownloadPlan`, `ExtensionSourceType.MODRINTH`, and `ServerExtensionsService.installCatalogDownload(...)`.
- Existing installed extension metadata stores provider/project/version identity in `ExtensionSource`, but Modrinth `.mrpack` export also needs file download hashes. If hashes are not currently persisted, the implementation may need to fetch version/file metadata from Modrinth during export or extend cached metadata carefully.
- Official Modrinth pack-format reference: `https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack`.

## Suggested Approach

Add a dedicated `ModrinthModpackService` instead of reshaping `CurseForgeModpackService` in place.

Suggested service responsibilities:

- Define Modrinth-specific records for pack index metadata, indexed files, dependencies, hashes, env/side flags, import summary, export summary, and skipped entries.
- Export from `Server.getExtensions()` by selecting extensions with `ExtensionSourceType.MODRINTH` and valid project/version IDs.
- Resolve Modrinth version/file metadata for export when hashes or download URLs are missing locally.
- Write `modrinth.index.json` plus selected overrides into a `.mrpack` ZIP.
- Read `.mrpack` ZIPs with `ZipFile`, reject unsafe paths, parse `modrinth.index.json`, and validate dependencies.
- Download indexed files with the existing downloader/client infrastructure where possible.
- Verify hashes before install.
- Install downloaded jars through `ServerExtensionsService` so compatibility checks, target directories, metadata, and cache behavior stay centralized.

Update `GestorServidores` with Modrinth wrappers parallel to the existing CurseForge wrappers. Keep naming explicit, e.g. `exportarModpackModrinth(...)`, `leerIndiceModpackModrinth(...)`, and `importarModpackModrinth(...)`.

Update `PanelExtensiones` so the modpack actions are format-aware:

- Export should default to Modrinth `.mrpack`.
- Import should accept `.mrpack` and route it to Modrinth import.
- If CurseForge ZIP support remains visible, label it as CurseForge clearly and warn/import-disable when no API key is configured.
- Keep user-facing copy in Spanish and avoid implying CurseForge import works without credentials.

When implementing, update `docs/pipelines/extensions-pipeline.md` so the modpack section names both CurseForge legacy behavior and Modrinth `.mrpack` behavior.

## Verification

- Run `mvn -q -DskipTests compile`.
- Add service tests for reading/writing valid `.mrpack` archives.
- Add tests for invalid/missing `modrinth.index.json`, bad ZIP paths, missing required fields, hash mismatch, optional files, and dependency mismatch warnings.
- Add tests or fakes around Modrinth metadata resolution when export needs hashes/download URLs.
- Manually export a mod server containing Modrinth-installed mods and verify the `.mrpack` contains a valid `modrinth.index.json`.
- Manually import the exported `.mrpack` into a clean compatible mod server and verify mods are downloaded, hash-checked, installed, detected, and persisted with Modrinth source metadata.
- Manually verify local/manual/CurseForge-only entries are reported as skipped rather than silently lost.
- Manually verify the old CurseForge path is not removed.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack`
