# App Persistence Uses Install Directory Instead Of User Data Directory

## Status

Pending

## Area

Application configuration, persistence paths, cache paths, and startup locks.

## Issue

Core app persistence is still based on `GestorConfiguracion.getBaseDirectory()`, which resolves near the running classes/JAR and may point to the project folder, `target/classes`, or the install directory. Meanwhile some cache code already uses the user folder under `~/.easy-mc-server/cache/...`. This splits app data between locations and can make installed builds fragile if the app directory is read-only, moved, replaced, or shared by multiple versions.

## Desired Behavior

Use one app-owned user data root for application-managed state, with clear subdirectories for persistent config, disposable cache, locks, and generated stats. Persistent files should not live inside the cache folder.

Suggested shape:

```text
~/.easy-mc-server/
  config/
    easy-mc-config.json
    easy-mc-server-list.json
  cache/
    extensions/
  locks/
    app.lock
  stats/
```

## Evidence

- `ExtensionIconLoader` stores icon cache under `~/.easy-mc-server/cache/extensions/icons`.
- `ExtensionHttpClient` stores HTTP cache under `~/.easy-mc-server/cache/extensions/http`.
- `GestorConfiguracion.getBaseDirectory()` resolves persistence relative to the running code location.
- `GestorConfiguracion` stores `easy-mc-config.json` beside the resolved base directory.
- `GestorUsuariosConocidos` and `PanelEstadisticas` also derive persisted paths from `GestorConfiguracion.getBaseDirectory()`.
- The planned single-instance lock needs a durable app-owned user path, but not a cache path.

## Suggested Approach

Add a central path helper, for example `controlador.AppPaths`, that exposes:

- `rootDirectory()`
- `configDirectory()`
- `cacheDirectory()`
- `locksDirectory()`
- `statsDirectory()`

Then migrate callers gradually:

- Keep cache files under `cache/`.
- Move app config, server list, known users, and stats to persistent user-data subdirectories.
- Use `locks/` for the single-instance lock file.
- Preserve compatibility by migrating existing files from the old base directory if the new target does not already exist.
- Use atomic writes where practical and surface IO failures with Spanish messages where the UI is involved.

## Verification

- Fresh launch creates the expected user-data directories.
- Existing config/server-list files are migrated from the old base directory when needed.
- Cache files remain disposable and separate from persistent config.
- App still loads known servers, preferences, known users, and stats after migration.
- `mvn -q -DskipTests compile`
- Targeted tests for path migration and config loading if path helpers are added.

## Related Docs

- `docs/pipelines/configuration-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/application-shell-pipeline.md`
