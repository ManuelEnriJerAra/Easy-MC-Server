# Vanilla Server Download Timeout Process

## Status

Fixed

## Linked Fix

- `docs/fixes/vanilla-server-download-timeout.md`

## Scope

Fix Vanilla server creation failures where a slow Mojang server jar download surfaces as `java.net.SocketTimeoutException: Read timed out`. The fix should keep JSON metadata timeouts short, make large jar downloads more tolerant, avoid leaving partial jar files, and show Spanish user-facing error text.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md`, server creation and platform adapter pipelines, and fix/process documentation standards. |
| DONE | 2. Implement the fix | Separated large-download timeout handling from metadata lookups and improved timeout messaging. |
| DONE | 3. Verify behavior | Added focused tests and verified them with JDK 25 targeted test and compile commands. |

## Implementation Notes

`MojangAPI` uses an 8-second read timeout for all requests. That is suitable for small JSON metadata but can fail during large Vanilla server jar downloads when no bytes arrive for a short period.

The download path now uses a 60-second read timeout, retries timeout failures up to three times, writes to `<target>.part`, and only replaces the final jar after the stream completes.

## Verification Notes

- `mvn -q "-Dtest=controlador.MojangAPITest,controlador.platform.ServerPlatformAdaptersTest" test` passed with JDK 25.
- `mvn -q -DskipTests compile` passed with JDK 25.
- Maven still emits the expected Lombok `sun.misc.Unsafe` warning noted by the project guidance.
