# Forge Loader Version Detected As Minecraft Process

## Status

Fixed

## Linked Fix

- `docs/fixes/forge-loader-version-detected-as-minecraft.md`

## Scope

Fix Forge server detection when a Forge runtime jar exposes the Forge loader version, such as `41.1.0`, through its own `version.json`. The fix keeps loader versions out of `Server.version` and preserves Minecraft-version detection from Forge run scripts, args, library coordinates, manifests, and real Minecraft server classes.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect detection flow | Traced Forge detection through `ForgeServerPlatformAdapter`, `AbstractServerPlatformAdapter`, `MinecraftServerVersionDetector`, and `MinecraftServerJarInspector`. |
| DONE | 2. Add Forge-safe detection | Added a Forge-specific detector that prefers metadata and Minecraft server classes, while skipping Forge-marked top-level jars as generic Minecraft-version sources. |
| DONE | 3. Add regression coverage | Added a Forge 1.19 / Forge 41.1.0 fixture to prove `41.1.0` is not persisted as the Minecraft version. |
| DONE | 4. Verify behavior | Ran focused Forge detection tests and compile. |

## Implementation Notes

Generic jar inspection reads `version.json` first because Vanilla and many platform jars expose the Minecraft version there. Forge runtime jars can instead expose the Forge loader version in that file. Since `41.1.0` is syntactically version-like, the shared parser accepted it as a future-style Minecraft version.

Forge now calls `MinecraftServerVersionDetector.detectForge(...)`. That method checks metadata files first, including run scripts and `libraries/net/minecraftforge/forge/<minecraft>-<forge>` coordinates, then checks the real `net/minecraft/server/MinecraftServer.class` if present, then checks non-Forge top-level jars. This avoids treating Forge loader jar metadata as the Minecraft version without hardcoding Forge version numbers.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#detect_noDebeUsarVersionForgeDelJarComoVersionMinecraft+detect_debeInferirVersionDesdeArgsDeForgeModernoSinJarEjecutable+detect_noDebeUsarVersionDeLoaderForgeComoVersionMinecraft" test`
- `mvn -q -DskipTests compile`
