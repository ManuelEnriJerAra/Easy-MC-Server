# Vanilla Server Download Timeout

## Status

Fixed

## Original Issue

Creating a Vanilla server could fail with a technical timeout message such as `java.net.SocketTimeoutException: Read timed out`.

## Root Cause

`MojangAPI.descargar(...)` reused the short 8-second metadata read timeout for full server jar downloads. A large jar download can legitimately pause longer than small JSON metadata requests, and the timeout was propagated to the UI through a raw exception message.

## Solution

Vanilla server jar downloads now use the dedicated large-download path in `MojangAPI.descargar(...)`:

- 60-second read timeout for file downloads.
- Up to three retries for read timeouts.
- Temporary `.part` file writes with final move only after a complete stream.
- Spanish timeout messages in server creation/download UI.

## Files Changed

- `src/main/java/controlador/MojangAPI.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/test/java/controlador/MojangAPITest.java`
- `docs/pipelines/platform-adapters-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.MojangAPITest,controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q -DskipTests compile`

Both commands passed with JDK 25. The expected Lombok `sun.misc.Unsafe` warning appeared.

## Detailed Process

- `docs/fixes/process/vanilla-server-download-timeout.md`

## Regression Notes

Keep short timeouts for Mojang metadata JSON, but do not reuse them for large binary downloads. Any new server jar or installer download path should avoid replacing the final file until the stream has completed.

## Related Docs

- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/platform-adapters-pipeline.md`
