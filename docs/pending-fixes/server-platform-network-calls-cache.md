# Platform Creation Network Calls Need Clearer Caching And Failure Behavior

## Status

Pending

## Area

`controlador.platform` remote version clients and server creation wizard.

## Issue

Platform creation options rely on remote APIs/repositories. Failure, latency, and caching behavior is spread across clients and wizard logic, which can lead to inconsistent user feedback or repeated network work.

## Desired Behavior

Remote version lookup should have consistent failure messages, caching policy, and refresh behavior across platform adapters.

## Evidence

- `FabricMetaClient`
- `QuiltMetaClient`
- `ForgeRepositoryClient`
- `NeoForgeRepositoryClient`
- `PaperDownloadsClient`
- `PurpurDownloadsClient`
- server creation wizard version availability checks

## Suggested Approach

Document and centralize a small policy for:

- timeout behavior
- error messages
- cache lifetime, if any
- refresh/retry behavior

Avoid adding persistent cache unless there is a clear UX need.

## Verification

- `mvn -q -DskipTests compile`
- Platform adapter tests.
- Manual server creation checks with network available and unavailable.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
