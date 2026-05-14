# Quilt Stable Version List Process

## Status

Fixed

## Linked Fix

- `docs/fixes/quilt-stable-version-list.md`

## Scope

Resolve misleading Quilt availability in the server creation wizard. The fix covers Quilt creation option listing only: use Quilt metadata's `stable` flag so snapshots and release candidates do not consume the option cap and hide older stable releases.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Check live metadata | Confirmed Quilt metadata currently includes older stable releases down to `1.14.4`. |
| DONE | 2. Implement stable filtering | Filtered Quilt game versions by the metadata `stable` flag before creating options. |
| DONE | 3. Verify behavior | Ran targeted platform tests and compile. |
| DONE | 4. Document fix | Added solved fix note and related pipeline note. |

## Implementation Notes

`QuiltMetaClient` already reads objects from `/v3/versions/game`, including the `stable` boolean. The wizard does not enable snapshot filtering for non-Vanilla platforms, so Quilt creation options should be stable releases only.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test` passed.
- `mvn -q -DskipTests compile` passed with the expected Lombok `sun.misc.Unsafe` warning.
