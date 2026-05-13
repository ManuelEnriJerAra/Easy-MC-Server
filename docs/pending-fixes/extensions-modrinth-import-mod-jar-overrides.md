# Modrinth Import Mod Jar Overrides

## Status

Pending

## Area

`controlador.extensions.ModrinthModpackService`

## Issue

Modrinth `.mrpack` import still allows override entries under `mods/*.jar`. `extractOverrides(...)` uses `isAllowedOverridePath(...)`, which permits top-level mod jars in `overrides/`, `server-overrides/`, and `client-overrides/`.

Those jars are copied after indexed downloads are installed, so they can overwrite verified Modrinth files or add unmanaged jars without Modrinth hash verification, compatibility validation, or installed-extension cache metadata.

## Desired Behavior

Modpack override extraction should not write mod jars into managed mod directories. Import should install mods through the indexed-file download path only, where hashes and compatibility are checked.

Config-style overrides from allowed server config paths should continue to work.

## Evidence

- `src/main/java/controlador/extensions/ModrinthModpackService.java`
  - `extractOverrides(...)`
  - `extractOverrideDirectory(...)`
  - `isAllowedOverridePath(...)`
- `isAllowedOverridePath(...)` currently returns true for safe-looking top-level `mods/*.jar` paths.
- `controlador.GestorServidores.importarModpackModrinth(...)` installs indexed files first, then calls `modrinthModpackService.extractOverrides(...)`.

## Suggested Approach

Split import and export override policy if needed, or tighten `isAllowedOverridePath(...)` so override extraction only permits config-style paths such as `config/` and `defaultconfigs/`.

Add a regression test with a `.mrpack` containing `server-overrides/mods/unmanaged.jar` and assert it is skipped or rejected instead of copied.

## Verification

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -Dtest=GestorServidoresTest test`
- `mvn -q -DskipTests compile`

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
