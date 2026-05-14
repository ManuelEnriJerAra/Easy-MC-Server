# Server Platform Snapshot Loader Downloads

## Status

Pending

## Area

`controlador.platform` remote version clients, `controlador.GestorServidores` server creation wizard, and mod/plugin loader platform adapters.

## Feature Request

Implement snapshot downloads for non-Vanilla server platforms and loaders, including mod and plugin server loaders where the upstream platform exposes snapshot, pre-release, release-candidate, experimental, or otherwise unstable Minecraft versions.

## Motivation

The server creation wizard already has a snapshots checkbox, but today that behavior is effectively Vanilla-focused. Users should be able to opt into snapshots for loaders and plugin/mod platforms instead of the app silently hiding those options.

## Desired Behavior

When the snapshots checkbox is unchecked, the wizard should show the latest stable release options for every supported platform.

When the snapshots checkbox is checked, the wizard should also show available snapshot/unstable options for platforms that support them.

The app should preserve platform-specific metadata so users can see what they are selecting, for example stable, snapshot, release candidate, experimental, loader version, and build channel where available.

No available version should be hidden only because a hard-coded list cap was reached before filtering release/snapshot state.

## Notes

- The requested model should follow the existing Vanilla release/snapshot checkbox behavior and generalize it to platform adapters.
- Current platform clients include `FabricMetaClient`, `QuiltMetaClient`, `ForgeRepositoryClient`, `NeoForgeRepositoryClient`, `PaperDownloadsClient`, and `PurpurDownloadsClient`.
- Upstream APIs expose different metadata shapes, so a shared internal creation-option model may need to normalize release channel, stability, loader version, build ID, and download URL behavior.
- Preserve Spanish UI terminology and keep the snapshots checkbox as the user-facing control unless a broader creation wizard redesign is requested.

## Suggested Approach

Introduce a shared platform creation metadata contract that includes a release channel or stability field, then have each adapter map its upstream API data into that contract.

Unify filtering in the wizard so Vanilla and non-Vanilla platforms use the same release/snapshot decision path.

Keep remote metadata fetching efficient: fetch full metadata once per platform where possible, cache through the shared in-memory metadata policy, and avoid per-version detail calls during list rendering unless the data is loaded lazily for a selected option.

## Verification

- `mvn -q -DskipTests compile`
- Targeted tests for each changed platform client.
- Wizard tests or focused unit coverage for release/snapshot filtering.
- Manual server creation checks for Vanilla and at least one mod loader and plugin loader with snapshots enabled and disabled.

## Related Docs

- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/platform-adapters-pipeline.md`
