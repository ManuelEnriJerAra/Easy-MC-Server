# Forge NeoForge Release Loader Fallbacks Process

## Status

Fixed

## Linked Fix

- `docs/fixes/forge-neoforge-release-loader-fallbacks.md`

## Scope

Fix Forge and NeoForge creation option listing when a stable Minecraft release only has beta/alpha loader artifacts available upstream. The fix keeps stable loader artifacts preferred, but prevents the Minecraft release from disappearing when no stable loader exists yet.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Verify live metadata shape | Checked current Forge/NeoForge Maven metadata and found release Minecraft versions with only beta loader artifacts, including NeoForge `26.1.2.*-beta`. |
| DONE | 2. Implement fallback selection | Added per-Minecraft-version fallback maps in Forge and NeoForge repository clients, used only when no stable loader artifact exists for a stable Minecraft release. |
| DONE | 3. Verify behavior | Added regression coverage for fallback release options and ran targeted platform tests plus the full test suite. |

## Implementation Notes

The snapshots checkbox still represents unstable Minecraft versions, not loader channels. Beta/alpha loader artifacts for stable Minecraft releases stay in the release bucket only as a last-resort fallback. If a stable loader is available, it wins over beta/alpha artifacts.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#forgeCreationOptions_debenSepararBuildsEstablesEInestables+neoForgeCreationOptions_debenIgnorarBuildsInestablesDeLoaderParaReleasesMinecraft" test`
- `mvn test`
