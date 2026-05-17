# Fabric Semantic Release Version Detection Process

## Status

Fixed

## Linked Fix

- `docs/fixes/fabric-semantic-release-version-detection.md`

## Scope

Fix Fabric server recognition when a modern semantic Minecraft release such as `26.1.2` is only available from the Fabric launcher jar artifact. Keep the change scoped to Fabric launcher artifacts so generic server detection does not start trusting arbitrary folder or jar names.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md`, platform/server lifecycle pipelines, and solved-fix/process standards. |
| DONE | 2. Implement the fix | Added Fabric installer jar markers, Fabric-scoped launcher artifact version extraction, and regression coverage. |
| DONE | 3. Verify behavior | Ran targeted platform/import regressions, the platform adapter suite, and `mvn -q -DskipTests compile`. |

## Implementation Notes

Live Fabric metadata exposes `26.1.2` as a stable game version. The generated server jar from Fabric's `/server/jar` endpoint is an installer/launcher jar with `net/fabricmc/installer/ServerLauncher.class` and no `version.json`, so the generic jar inspector cannot read the Minecraft version from contents alone.

The fix keeps the generic detector strict. Fabric gets a dedicated detection path that still prefers jar `version.json` and metadata files, then falls back to the Fabric launcher artifact name pattern `fabric-server-mc.<minecraft>-loader.<loader>-launcher.<launcher>.jar`.

## Verification Notes

- `mvn -q -Dtest=ServerPlatformAdaptersTest#detect_debeInferirVersionSemanticaDesdeJarLauncherDeFabric test` passed.
- `mvn -q -Dtest=GestorServidoresTest#importarServidorDebeAceptarVersionSemanticaFuturaDesdeJarLauncherFabric test` passed.
- `mvn -q -Dtest=ServerPlatformAdaptersTest test` passed.
- `mvn -q -DskipTests compile` passed.
