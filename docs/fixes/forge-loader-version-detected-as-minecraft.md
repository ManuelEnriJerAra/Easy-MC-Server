# Forge Loader Version Detected As Minecraft

## Status

Fixed

## Original Issue

Forge 1.19 servers could be detected as Minecraft `41.1.0`. That value is the Forge loader version, not the Minecraft version.

## Root Cause

Forge runtime jars can expose the Forge loader version in `version.json`. The shared jar inspector read that field before metadata files, and the shared version regex accepted `41.1.0` as a valid future-style semantic Minecraft version.

## Solution

Forge detection now uses a Forge-specific Minecraft-version path that prefers run scripts, Forge args, library coordinates, modpack manifests, and real Minecraft server classes. It skips Forge-marked top-level jars as generic Minecraft-version sources, so Forge loader metadata cannot overwrite `Server.version`.

## Files Changed

- `src/main/java/controlador/platform/ForgeServerPlatformAdapter.java`
- `src/main/java/controlador/platform/MinecraftServerVersionDetector.java`
- `src/main/java/controlador/platform/MinecraftServerJarInspector.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `docs/pipelines/platform-adapters-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#detect_noDebeUsarVersionForgeDelJarComoVersionMinecraft+detect_debeInferirVersionDesdeArgsDeForgeModernoSinJarEjecutable+detect_noDebeUsarVersionDeLoaderForgeComoVersionMinecraft" test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/forge-loader-version-detected-as-minecraft.md`

## Regression Notes

Do not treat platform loader jar `version.json` values as Minecraft versions for Forge. Prefer explicit Minecraft context and keep loader versions in loader metadata fields only.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
