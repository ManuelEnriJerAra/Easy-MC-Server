# Forge Old Artifact Version Parsing

## Status

Fixed

## Original Issue

Forge Maven metadata contains older artifact versions with extra Minecraft branch suffixes, such as `1.10-12.18.0.2000-1.10.0`. The app could parse those artifacts as pseudo Minecraft versions like `1.10-12.18.0.2000`, producing misleading creation rows.

## Root Cause

`ForgeRepositoryClient` split artifact versions with a greedy left-hand group. For old Forge artifacts, the final suffix looked like the Forge version, so everything before it was treated as the Minecraft version.

## Solution

`ForgeRepositoryClient` now parses the Minecraft version from the artifact prefix first, then keeps the rest of the artifact string as the Forge/platform version. This preserves full installer artifact coordinates while grouping old variants under the real Minecraft version.

Regression coverage verifies `1.7.10`, `1.8.9`, and `1.10` old artifact shapes group correctly and do not display pseudo-Minecraft labels.

## Files Changed

- `src/main/java/controlador/platform/ForgeRepositoryClient.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `docs/pipelines/platform-adapters-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#forgeCreationOptions_debenParsearArtefactosAntiguosPorVersionMinecraftReal+forgeCreationOptions_debenSepararBuildsEstablesEInestables" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q -DskipTests compile`

Commands were run with JDK 25 via `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot`.

## Detailed Process

- `docs/fixes/process/forge-old-artifact-version-parsing.md`

## Regression Notes

Forge artifact parsing should treat the first Minecraft-shaped prefix as the game version. Do not parse Forge artifacts from the right unless branch suffixes such as `-1.7.10` and `-1.10.0` are explicitly handled.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
