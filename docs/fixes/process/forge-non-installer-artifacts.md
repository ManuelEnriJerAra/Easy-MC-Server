# Forge Non Installer Artifacts Process

## Status

Fixed

## Linked Fix

- `docs/fixes/forge-non-installer-artifacts.md`

## Scope

Hide Forge creation options that the current automated installer flow cannot install. This covers old Forge Maven artifacts before installer jars were published, such as Minecraft `1.1`, without adding support for legacy client/server ZIP artifact layouts.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Review reference fix | Read the Vanilla non-downloadable-version fix and confirmed it uses a conservative known-availability cutoff. |
| DONE | 2. Implement Forge filter | Added a Forge installer availability cutoff and regression coverage for pre-installer artifacts. |
| DONE | 3. Verify and document | Ran targeted tests and compile, then updated solved docs and pipeline guidance. |

## Implementation Notes

Forge Maven metadata includes old coordinates such as `1.1-1.3.4.29`, but those coordinates expose legacy ZIP artifacts rather than `forge-<artifactVersion>-installer.jar`. Easy MC currently installs Forge through the installer jar URL, so those options should not be listed.

Checked representative Maven artifacts:

- `1.1-1.3.4.29`: installer jar is not available.
- `1.2.5-3.4.9.171`: installer jar is not available.
- `1.4.7-6.6.2.534`: installer jar is not available.
- `1.5-7.7.0.582`: installer jar is not available.
- `1.5.2-7.8.1.738`: installer jar is available.

`ForgeRepositoryClient` now excludes release-shaped Minecraft versions older than `1.5.2` before grouping creation options. This keeps listing lightweight and avoids one remote probe per artifact while matching the current installer-based install flow.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#forgeCreationOptions_debenExcluirArtefactosSinInstalador+forgeCreationOptions_debenParsearArtefactosAntiguosPorVersionMinecraftReal+forgeCreationOptions_debenSepararBuildsEstablesEInestables" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q -DskipTests compile`

All commands were run with `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot`.
