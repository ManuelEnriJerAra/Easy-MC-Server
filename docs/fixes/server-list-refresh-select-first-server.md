# Server List Refresh Select First Server

## Status

Fixed

## Original Issue

After a manual refresh removed the selected server because its folder had been deleted on disk, the right panel stayed empty even when other managed servers were still available.

## Root Cause

The refresh handler cleared the stale selection and right-side content, but unlike the explicit remove-server flow it did not pick a replacement server from the remaining list.

## Solution

`VentanaPrincipal` now reuses the existing first-selection behavior after a refresh removes the current server. If other servers remain, the first one is selected automatically; the right panel is only cleared when the list becomes empty.

## Files Changed

- `src/main/java/vista/VentanaPrincipal.java`
- `docs/pipelines/application-shell-pipeline.md`
- `docs/fixes/process/server-list-refresh-select-first-server.md`

## Verification

- Manual check: delete the selected server folder outside the app, click refresh, and confirm the first remaining server is selected and rendered on the right.

## Detailed Process

- `docs/fixes/process/server-list-refresh-select-first-server.md`

## Regression Notes

Manual list refresh and explicit remove flows should keep the same post-removal selection behavior whenever the managed list still contains servers.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
