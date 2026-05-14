# Server Platform Network Calls Cache

## Status

Fixed

## Original Issue

Platform creation version lookups used remote APIs and Maven repositories without a shared cache or failure policy. Switching platforms or retrying after a failed lookup could repeat identical network work and surface inconsistent errors.

## Root Cause

JSON-based clients shared a low-level HTTP helper but did not cache responses, while Forge and NeoForge opened Maven metadata XML streams independently. The server creation wizard also discarded loaded options when moving between platforms.

## Solution

Added `PlatformRemoteLookupPolicy` for in-memory metadata caching, shared timeouts, and consistent remote lookup failures. JSON metadata now goes through `CachedPlatformHttpClient`, Forge/NeoForge metadata XML is cached as bytes before parsing, and the creation wizard keeps loaded options per platform during one dialog session.

## Files Changed

- `src/main/java/controlador/platform/PlatformRemoteLookupPolicy.java`
- `src/main/java/controlador/platform/PlatformHttpClient.java`
- `src/main/java/controlador/platform/FabricMetaClient.java`
- `src/main/java/controlador/platform/QuiltMetaClient.java`
- `src/main/java/controlador/platform/PaperDownloadsClient.java`
- `src/main/java/controlador/platform/PurpurDownloadsClient.java`
- `src/main/java/controlador/platform/ForgeRepositoryClient.java`
- `src/main/java/controlador/platform/NeoForgeRepositoryClient.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/server-platform-network-calls-cache.md`

## Regression Notes

Remote platform metadata should remain in-memory only unless a future UX explicitly requires persistence. Keep actual server jar and installer downloads uncached by this metadata policy.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
