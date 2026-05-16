# Background Loading And Metadata Cache For Heavy Server Pages

## Status

Pending

## Progress Notes

Partially implemented in `docs/features/server-pages-metadata-cache-background-loading.md`:

- `WorldDataReader` now exposes a one-read `WorldMetadata` snapshot for `level.dat`.
- `PanelMundo` reuses one snapshot across labels, settings, and gamerule rendering during a refresh.
- `PanelJugadores` refreshes list counters and known-user sync in a background worker.

Remaining work in this pending feature:

- full loading placeholders and async page-level refresh for Mundo;
- async history loading and cached `server.properties` reads for Estadisticas;
- optional async recent-connection data loading in `WorldRecentConnectionsPanel`.

## Area

`vista.PanelMundo`, `vista.PanelEstadisticas`, `vista.PanelJugadores`, and the right-side server-detail loading flow.

## Feature Request

Add background loading and shared metadata caching for heavy server-detail pages so first-open page loads also avoid blocking the EDT.

## Motivation

The server-selection freeze was reduced by lazy-loading hidden pages, but several page constructors and refresh paths still perform synchronous file and metadata work when those pages are opened. Large worlds, many playerdata files, or big stats histories can still make specific pages feel slow.

## Desired Behavior

Heavy server pages should show immediately with a lightweight loading state, then populate their expensive data asynchronously. Repeated reads of the same world metadata should reuse shared cached data within one page refresh instead of reparsing the same files many times.

## Notes

- `PanelMundo.refrescarDatos()` synchronously triggers world sync, metadata reads, storage analysis, preview reads, and recent-connection refresh.
- `WorldDataReader` currently reparses `level.dat` across many getter calls.
- `PanelEstadisticas` loads history/config data during construction.
- `PanelJugadores` still performs synchronous list/property reads for counters and known-user sync.
- `PanelPrevisualizacion` uses an EDT Swing timer that rereads `server.properties` periodically to refresh the shown port.

## Suggested Approach

Start with the highest-impact improvements:

- add page-level loading placeholders plus `SwingWorker` or equivalent background refresh for Mundo and Estadísticas
- introduce a shared world metadata snapshot/cache per refresh so `level.dat` is parsed once and reused
- move non-urgent player/admin counter refreshes off the selection path

Keep the UI state transitions explicit and always marshal Swing mutations back to the EDT.

## Verification

- `mvn -q -DskipTests compile`
- manual checks with a server that has a large world, many playerdata files, and a non-trivial stats history
- confirm the page shell appears immediately and later fills in data without freezing the app

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
