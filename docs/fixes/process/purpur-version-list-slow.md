# Purpur Version List Slow Process

## Status

Fixed

## Linked Fix

- `docs/fixes/purpur-version-list-slow.md`

## Scope

Resolve slow Purpur version loading in the server creation wizard. The fix covers the Purpur creation option lookup path only and avoids changing actual jar download behavior for explicit build IDs.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Diagnose request pattern | Confirmed `PurpurDownloadsClient` calls the project endpoint and then one version detail endpoint per Minecraft version. |
| DONE | 2. Implement faster listing | Used Purpur's `latest` download alias for creation options so listing does not need per-version detail calls. |
| DONE | 3. Verify behavior | Ran targeted platform tests and compile. |
| DONE | 4. Document fix | Added solved fix note and relevant pipeline note. |

## Implementation Notes

Purpur's project endpoint returns the Minecraft version list but not the latest build per version. The previous implementation resolved that build number during option listing, turning one list load into one project request plus one version-detail request per Minecraft version. The Purpur API accepts `latest` as the build segment in download URLs, so creation options now store `latest` and defer concrete build resolution to the remote API.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test` passed.
- `mvn -q -DskipTests compile` passed with the expected Lombok `sun.misc.Unsafe` warning.
