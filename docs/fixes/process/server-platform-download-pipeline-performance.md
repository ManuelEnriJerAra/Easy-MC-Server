# Server Platform Download Pipeline Performance Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-platform-download-pipeline-performance.md`

## Scope

Resolve the pending issue where server creation platform metadata could be capped, filtered inconsistently, or loaded with avoidable per-version remote calls. The fix covers creation option stability metadata, wizard release/snapshot filtering, platform option listing, and lazy Paper build resolution.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `AGENTS.md`, `docs/README.md`, `platform-adapters-pipeline.md`, `server-creation-pipeline.md`, and the pending fix. |
| DONE | 2. Implement the fix | Normalized option stability, removed arbitrary list caps, avoided Paper per-version build lookups during listing, and updated wizard filtering. |
| DONE | 3. Verify behavior | Ran targeted platform tests, compile, and the full test suite. |

## Implementation Notes

- Keep actual jar and installer downloads outside metadata caching.
- Prefer lazy detail resolution for Paper builds because the REST endpoint requires a per-version build lookup for stable build URLs.
- Preserve the existing wizard release/snapshot checkboxes and make adapter support explicit instead of hard-coding Vanilla only.
- Fabric and Quilt now return both stable and unstable game metadata; the wizard decides which entries are visible.
- Forge and NeoForge keep the latest stable option for stable Minecraft releases, fall back to the latest downloadable loader artifact only when no stable loader exists for that Minecraft release, and list snapshot-targeted artifacts individually so beta-style artifacts cannot displace stable releases in the default filter.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test` passed.
- `mvn -q -DskipTests compile` passed with the expected Lombok `sun.misc.Unsafe` warning.
- `mvn test` passed: 269 tests run, 0 failures, 0 errors, 1 skipped. The suite emitted expected test warnings from fake failing extension providers and the Lombok `sun.misc.Unsafe` warning.
