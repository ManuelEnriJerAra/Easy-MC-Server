# NeoForge 1.21.10 Version Inference Process

## Status

Fixed

## Linked Fix

- `docs/fixes/neoforge-1-21-10-version-inference.md`

## Scope

Fix NeoForge creation option inference for modern 1.21 patch versions whose artifact versions look like `21.10.64`. The fix is limited to NeoForge Minecraft-version inference and regression coverage.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Verify upstream metadata | Checked NeoForge Maven metadata and confirmed `21.10.*` artifacts exist. |
| DONE | 2. Implement the fix | Adjusted `NeoForgeRepositoryClient.inferMinecraftVersion(...)` so legacy `21.x.y` artifacts map to Minecraft `1.21.x`. |
| DONE | 3. Verify behavior | Added regression coverage for `21.10.64` and ran the targeted platform tests. |

## Implementation Notes

NeoForge has two relevant artifact-shape families in this code path:

- Legacy 1.x targets such as `21.10.64`, where the first number is the Minecraft minor version and the second number is the Minecraft patch version. This should infer `1.21.10`.
- Future semantic targets such as `26.1.2.48-beta`, where the first three numbers are the Minecraft version. This should infer `26.1.2`.

The semantic branch now starts at major `22` and above so current `21.*` NeoForge artifacts stay on the legacy mapping.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#creationClients_debenParsearOpcionesYDirectorios+neoForgeCreationOptions_debenIgnorarBuildsInestablesDeLoaderParaReleasesMinecraft" test`
