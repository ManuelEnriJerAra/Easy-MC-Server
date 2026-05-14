# Server Platform Download Pipeline Performance And Filtering

## Status

Pending

## Area

`controlador.platform` remote version clients, `controlador.GestorServidores` server creation wizard, platform install/download URL resolution, and related tests.

## Issue

The server platform creation/download pipeline still has ad hoc behavior across adapters. Some clients cap visible options, some resolve extra remote metadata during list rendering, and the wizard snapshot/release filtering is not consistently applied outside Vanilla.

This can hide valid versions from the player, make platform selection slower than necessary, and make future loader snapshot support harder to implement cleanly.

## Desired Behavior

Do not hide available platform versions from the player because of arbitrary pre-filter caps or unstable entries consuming a cap.

Show the latest stable releases by default. When the snapshots checkbox is checked, include snapshots and other unstable versions for platforms that support them.

Use one consistent filtering path for Vanilla and non-Vanilla platforms.

Avoid performance issues in the download/version pipeline:

- no serial per-version remote calls during list rendering when an upstream API supports bulk metadata or aliases
- lazy-load expensive details only for the selected option when needed
- cache remote metadata through the shared in-memory platform metadata policy
- keep actual jar and installer downloads separate from metadata caching

## Evidence

- `GestorServidores` currently enables snapshot filtering only for Vanilla through `soportaSnapshotsCreacion(...)`.
- `ServerCreationOption` has `versionType`, but most non-Vanilla clients do not populate equivalent release/snapshot metadata.
- `QuiltMetaClient` previously allowed unstable metadata to affect the visible option set before stable filtering.
- `PurpurDownloadsClient` previously resolved latest builds with one detail request per Minecraft version.
- Several clients historically used hard-coded option caps such as 40, which can hide valid versions if filtering happens in the wrong place.
- User expectation: do not hide information; show latest stable release and snapshots when the checkbox is checked.

## Suggested Approach

Audit every platform creation client and installation path as one pipeline instead of isolated clients.

Create a normalized creation option/loadable version model with explicit stability/channel information. Use it from Vanilla and non-Vanilla adapters so the wizard can share one release/snapshot filter.

Remove or replace arbitrary caps with UI-side virtualization, search/filtering, paging, or post-filter limits that never hide the latest stable release and never let unstable entries displace stable ones.

Review download URL resolution separately from option listing. Prefer upstream aliases or bulk metadata for list rendering, and resolve exact build details lazily only when the selected installation requires them.

Add regression tests for:

- release-only view
- snapshots-enabled view
- stable versions not hidden by snapshots or caps
- remote metadata calls not growing linearly with version count for clients that support bulk metadata or aliases
- install URL resolution for explicit and latest/alias versions

## Verification

- `mvn -q -DskipTests compile`
- Targeted platform adapter tests.
- `mvn test` if shared filtering or installation contracts change.
- Manual creation wizard checks with snapshots enabled and disabled for Vanilla, Quilt, Fabric, Paper, Purpur, Forge, and NeoForge.
- Manual checks with network unavailable or slow to confirm errors and loading behavior stay consistent.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
- `docs/fixes/server-platform-network-calls-cache.md`
- `docs/fixes/purpur-version-list-slow.md`
- `docs/fixes/quilt-stable-version-list.md`
