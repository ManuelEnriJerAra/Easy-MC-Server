# Server Platform Network Calls Cache Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-platform-network-calls-cache.md`

## Scope

Resolve the pending fix for repeated and inconsistent remote metadata lookups during server platform creation. The fix covers in-memory caching, short-lived failure cooldowns, consistent metadata lookup errors, and documentation for the policy. It intentionally avoids persistent caches.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `AGENTS.md`, `docs/README.md`, platform adapter pipeline, server creation pipeline, and the pending fix note. |
| DONE | 2. Implement the fix | Added a shared remote metadata policy and wired JSON/XML creation clients through it. |
| DONE | 3. Verify behavior | Ran the targeted platform adapter test class and compile. |
| DONE | 4. Move documentation | Added solved fix note, updated related pipeline docs, and removed the pending fix note. |

## Implementation Notes

The platform clients previously performed fresh remote calls for every metadata request. JSON clients used `PlatformHttpClient`, while Forge and NeoForge opened metadata XML streams directly.

`PlatformRemoteLookupPolicy` now centralizes timeout constants, a 10-minute successful metadata cache, a 30-second failure cooldown, and the shared remote lookup failure message. `CachedPlatformHttpClient` applies that policy to JSON metadata, and Forge/NeoForge repository clients apply it to XML bytes before parsing.

The server creation wizard also keeps loaded options by platform for the lifetime of the dialog, so switching back to a previously selected platform reuses the already loaded option list.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test` passed.
- `mvn -q -DskipTests compile` passed with the expected Lombok `sun.misc.Unsafe` warning.
