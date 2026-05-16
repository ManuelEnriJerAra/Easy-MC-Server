# App Data Persistence Location

## Status

Fixed

## Original Issue

Core app persistence used executable-relative paths from `GestorConfiguracion.getBaseDirectory()`, which could point to the project folder, `target/classes`, or the install directory. Extension caches already used `~/.dora/cache/...`, so app data was split across locations and installed builds could write into fragile or read-only directories.

## Root Cause

Each persistence area resolved its own base path. Config and server-list files duplicated code-source directory detection, known users reused that same base directory, fallback stats used `base/stats`, and extension cache paths hard-coded the user-home cache root independently.

## Solution

Added `controlador.AppPaths` as the central app-owned user data root helper:

- `rootDirectory()`: `~/.dora`
- `configDirectory()`: persistent config and app lists
- `cacheDirectory()`: disposable cache data
- `locksDirectory()`: future startup/single-instance locks
- `statsDirectory()`: generated app stats fallback files

`GestorConfiguracion`, default `GestorServidores`, `GestorUsuariosConocidos`, extension HTTP/icon caches, and fallback stats history now use this helper. Existing executable-relative files are migrated into the new location when the new target file does not already exist.

## Files Changed

- `src/main/java/controlador/AppPaths.java`
- `src/main/java/controlador/GestorConfiguracion.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/controlador/GestorUsuariosConocidos.java`
- `src/main/java/controlador/extensions/ExtensionHttpClient.java`
- `src/main/java/vista/ExtensionIconLoader.java`
- `src/main/java/vista/PanelEstadisticas.java`
- `src/test/java/controlador/AppPathsTest.java`
- `docs/pipelines/configuration-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q -Dtest=AppPathsTest test`

Both passed with the expected Maven `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/fixes/process/app-data-persistence-location.md`

## Regression Notes

New app-managed persistence should go through `AppPaths`; avoid code-source, JAR, install-directory, or ad hoc `user.home` resolution in new callers. Cache files belong under `cache/`, persistent settings under `config/`, lock files under `locks/`, and app-generated fallback stats under `stats/`.

## Related Docs

- `docs/pipelines/configuration-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/application-shell-pipeline.md`
