# Forge Non Installer Artifacts

## Status

Fixed

## Original Issue

Forge server creation could show old Forge versions such as Minecraft `1.1` even though Easy MC installs Forge through `forge-<artifactVersion>-installer.jar` and those old Maven coordinates only expose legacy ZIP artifacts.

## Root Cause

`ForgeRepositoryClient` mapped every parseable Forge Maven metadata entry into a creation candidate. Forge metadata includes releases older than the installer era, but the automated install path does not support those old client/server ZIP artifact layouts.

## Solution

Forge creation options now exclude release-shaped Minecraft versions older than `1.5.2`, the first checked Forge Minecraft release with an installer jar. The installer URL remains based on the full Forge artifact coordinate for supported versions.

## Files Changed

- `src/main/java/controlador/platform/ForgeRepositoryClient.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `docs/pipelines/platform-adapters-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#forgeCreationOptions_debenExcluirArtefactosSinInstalador+forgeCreationOptions_debenParsearArtefactosAntiguosPorVersionMinecraftReal+forgeCreationOptions_debenSepararBuildsEstablesEInestables" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q -DskipTests compile`

Commands were run with JDK 25 via `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot`.

## Detailed Process

- `docs/fixes/process/forge-non-installer-artifacts.md`

## Regression Notes

Do not list Forge versions the current installer flow cannot install. If legacy ZIP-based Forge support is added later, it should be a separate install path with its own validation and tests rather than re-enabling these entries in the installer-based list.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
