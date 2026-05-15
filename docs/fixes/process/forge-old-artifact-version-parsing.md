# Forge Old Artifact Version Parsing Process

## Status

Fixed

## Linked Fix

- `docs/fixes/forge-old-artifact-version-parsing.md`

## Scope

Fix Forge creation option parsing for old Maven artifact versions that append an extra Minecraft branch suffix after the Forge build. The fix is limited to Forge metadata parsing and regression coverage; it should not add legacy support for unrelated platforms or change installer URL construction.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md`, `docs/pipelines/platform-adapters-pipeline.md`, and the pending-fix note. |
| DONE | 2. Implement parser and tests | Replaced greedy Forge artifact splitting with Minecraft-prefix parsing and added regression cases for old artifact shapes. |
| DONE | 3. Verify behavior | Ran focused Forge tests, the full platform adapter test class, and compile under JDK 25. |

## Implementation Notes

Forge artifact versions start with the Minecraft target, but older artifacts can append another Minecraft-looking branch suffix after the Forge build. Parsing from the right makes those suffixes look like the main Minecraft version. The parser now identifies the Minecraft prefix first and keeps the remaining artifact suffix as the Forge/platform version for display and installer URLs.

Regression coverage uses old artifacts including `1.7.10-10.13.4.1614-1.7.10`, `1.8.9-11.15.1.2318-1.8.9`, and `1.10-12.18.0.2000-1.10.0`. Those artifacts must produce one option per real Minecraft version, not pseudo rows such as `1.10-12.18.0.2000`.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#forgeCreationOptions_debenParsearArtefactosAntiguosPorVersionMinecraftReal+forgeCreationOptions_debenSepararBuildsEstablesEInestables" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q -DskipTests compile`

All commands were run with `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot` because the default shell Java was JDK 8.
