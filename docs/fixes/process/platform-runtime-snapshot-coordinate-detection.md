# Platform Runtime Snapshot Coordinate Detection Process

## Status

Fixed

## Linked Fix

- `docs/fixes/platform-runtime-snapshot-coordinate-detection.md`

## Scope

Preserve Minecraft snapshot/pre-release identity when Forge and NeoForge servers are detected from runtime library coordinates or launch arguments.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Review detection patterns | Found Forge and NeoForge coordinate regexes truncating snapshot/pre-release artifact coordinates. |
| DONE | 2. Implement the fix | Expanded coordinate capture so Forge keeps `1.20.3-pre1` and NeoForge keeps full artifact coordinates before inference. |
| DONE | 3. Verify behavior | Added runtime-coordinate regression tests and ran targeted platform tests. |

## Implementation Notes

Forge coordinates such as `1.20.3-pre1-49.0.0` must infer Minecraft `1.20.3-pre1`, not `1.20.3`. NeoForge coordinates such as `26.1.0.0-alpha.2+snapshot-1` must be passed whole to `NeoForgeRepositoryClient.inferMinecraftVersion(...)` so build metadata can infer `26.1-snapshot-1`.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#detect_debeInferirSnapshotForgeDesdeCoordenadaRuntime+detect_debeInferirSnapshotNeoForgeDesdeCoordenadaRuntime+creationClients_debenParsearOpcionesYDirectorios" test`
