# App Data Persistence Location Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/app-data-persistence-location.md`

## Scope

Resolve the pending issue where app-managed persistence could be written beside the running classes, JAR, or install directory. This covers app config, the server list, known users, extension caches, lock/stats path helpers, and fallback stats files for servers without a server directory. Server-specific stats already stored inside the server directory remain there.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked the configuration, filesystem/path, and application shell pipeline docs before editing persistence code. |
| DONE | 2. Map affected paths | Found executable-relative persistence in `GestorConfiguracion`, `GestorServidores`, `GestorUsuariosConocidos`, and fallback stats history, plus separate cache roots in extension HTTP/icon caches. |
| DONE | 3. Implement central paths | Added `controlador.AppPaths` and migrated default config/server-list/known-user/stats/cache paths to the user data root. |
| DONE | 4. Preserve compatibility | Kept legacy executable-relative migration for existing config files, server lists, known users, and fallback stats files when the new target is missing. |
| DONE | 5. Verify behavior | Ran compile and focused path migration tests. |

## Implementation Notes

`AppPaths` exposes `rootDirectory()`, `configDirectory()`, `cacheDirectory()`, `locksDirectory()`, and `statsDirectory()`. The default root is `~/.easy-mc-server`, with a test override through `easy.mc.appRoot`.

Legacy migration still resolves the old install/code-adjacent directory through `AppPaths.legacyBaseDirectory()`. When a new target file does not exist, old files are moved into the new config/stats locations.

Default app config and server list writes now create their parent directories before writing, so a fresh user data root does not fail because `config/` is absent.

## Verification Notes

- `mvn -q -DskipTests compile`
- `mvn -q -Dtest=AppPathsTest test`

Both commands passed. Maven emitted the expected `sun.misc.Unsafe` warning.
