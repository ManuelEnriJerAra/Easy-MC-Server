# Vanilla Non-Downloadable Server Versions Process

## Status

Fixed

## Linked Fix

- `docs/fixes/vanilla-non-downloadable-server-versions.md`

## Scope

Resolve the pending issue where Vanilla server creation can offer known Mojang release versions that do not have downloadable server jars, such as `1.0` and `1.1`. Keep the fix scoped to Vanilla creation options and preserve the existing selected-version URL validation as a fallback.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked pending-fix, solved-fix, platform adapter, and server creation documentation. |
| DONE | 2. Implement the fix | Added a conservative Vanilla option prefilter, server-jar URL caching, wizard removal of known-unavailable options, and regression coverage. |
| DONE | 3. Verify behavior | Targeted tests, compile, and full Maven test suite passed. |

## Implementation Notes

Mojang's manifest does not include server download URLs directly; it only links per-version metadata. The wizard already validates the selected Vanilla version through `obtenerUrlServerJar(...)`. This fix should avoid a broad per-version network fanout when listing Vanilla options and remove known release versions before the first official downloadable server jar.

`VanillaServerPlatformAdapter` now excludes release IDs older than `1.2.5`, the first Mojang Java release in current metadata with a server jar. Snapshots remain listed because the app only includes the Mojang `snapshot` type and the wizard still validates the selected version URL before advancing.

`MojangAPI.obtenerUrlServerJar(...)` now caches positive and negative server-jar URL lookups in memory. This avoids repeating the same manifest/detail lookup between the wizard check and later install path, without adding persistent stale metadata.

The server creation wizard now filters out Vanilla options already proven unavailable in the current wizard session. When the background URL check marks the selected version unavailable, the visible list is rebuilt and the next candidate is checked.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#creationClients_vanillaDebeExcluirReleasesSinServerJarConocido+install_vanillaDebeEncapsularDescargaEulaYMetadatos" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q "-Dtest=controlador.GestorServidoresTest" test`
- `mvn -q -DskipTests compile`
- `mvn test`
