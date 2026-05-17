# Fabric Semantic Release Version Detection

## Status

Fixed

## Original Issue

Fabric servers for modern semantic Minecraft releases such as `26.1.2` could be recognized as Fabric without persisting the Minecraft version.

## Root Cause

Fabric Meta's server jar can be an installer/launcher jar with no `version.json` and no Fabric Loader runtime classes before the server has completed setup. Dora's generic detector intentionally avoids arbitrary jar-name parsing, so a Dora-created Fabric launcher artifact such as `fabric-server-mc.26.1.2-loader.0.19.2-launcher.1.1.1.jar` did not provide a version hint.

## Solution

Fabric installer launcher classes now count as Fabric markers, and the Fabric adapter has a scoped fallback that extracts the Minecraft version from Fabric launcher artifact names shaped like `fabric-server-mc.<minecraft>-loader.<loader>-launcher.<launcher>.jar`. Generic detection remains strict and does not use arbitrary folder or jar names as version evidence.

## Files Changed

- `src/main/java/controlador/platform/FabricServerPlatformAdapter.java`
- `src/main/java/controlador/platform/MinecraftServerJarInspector.java`
- `src/main/java/controlador/platform/MinecraftServerVersionDetector.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `docs/pipelines/platform-adapters-pipeline.md`

## Verification

- `mvn -q -Dtest=ServerPlatformAdaptersTest#detect_debeInferirVersionSemanticaDesdeJarLauncherDeFabric test`
- `mvn -q -Dtest=GestorServidoresTest#importarServidorDebeAceptarVersionSemanticaFuturaDesdeJarLauncherFabric test`
- `mvn -q -Dtest=ServerPlatformAdaptersTest test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/fabric-semantic-release-version-detection.md`

## Regression Notes

Keep Fabric launcher artifact parsing scoped to Fabric. If Fabric changes the server jar name or starts writing an explicit metadata file, prefer the explicit metadata path and add coverage before broadening generic jar-name parsing.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
