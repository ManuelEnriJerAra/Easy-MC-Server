# Vanilla Non-Downloadable Server Versions

## Status

Fixed

## Original Issue

Vanilla server creation could show Mojang release versions that are present in the launcher manifest but do not expose a downloadable Java server jar. Early releases such as `1.0` and `1.1` could appear in the list and only fail later when the wizard or install path checked for a server download URL.

## Root Cause

`VanillaServerPlatformAdapter.listCreationOptions()` mapped every Mojang manifest entry of type `release` or `snapshot` into a creation option. Mojang's top-level manifest does not include server download availability, and checking every version JSON during list rendering would require hundreds of remote calls.

## Solution

Vanilla creation options now exclude release IDs older than `1.2.5`, the first current Mojang Java release with a server jar. The existing selected-version server-jar URL check remains the final guard for any future metadata anomaly.

`MojangAPI.obtenerUrlServerJar(...)` now caches positive and negative server-jar URL lookups in memory, and Vanilla installation uses the `MojangAPI` supplied by `ServerInstallationRequest` when present so the wizard and install path can share lookup behavior.

The server creation wizard now removes Vanilla options already proven unavailable in the current wizard session, so a failed lazy URL check does not leave a dead option visible.

## Files Changed

- `src/main/java/controlador/MojangAPI.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/controlador/platform/VanillaServerPlatformAdapter.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `docs/pipelines/platform-adapters-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#creationClients_vanillaDebeExcluirReleasesSinServerJarConocido+install_vanillaDebeEncapsularDescargaEulaYMetadatos" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q "-Dtest=controlador.GestorServidoresTest" test`
- `mvn -q -DskipTests compile`
- `mvn test`

## Detailed Process

- `docs/fixes/process/vanilla-non-downloadable-server-versions.md`

## Regression Notes

Do not make Vanilla option listing fetch every per-version JSON from Mojang. Keep list rendering lightweight, keep the selected-version URL check as the final guard, and add targeted known-availability rules only when they are stable and documented.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
