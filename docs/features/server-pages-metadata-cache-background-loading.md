# Server Pages Metadata Cache And Background Counter Loading

## Status

Implemented

## Feature

Heavy server pages now avoid some repeated synchronous metadata work when opened or refreshed.

## Motivation

Opening Mundo and Jugadores could perform repeated file reads on the EDT. Mundo repeatedly parsed `level.dat` across several labels and metadata sections, while Jugadores read list JSON files and synchronized known users immediately during construction/refresh.

## Solution

- `WorldDataReader` exposes a `WorldMetadata` snapshot that reads `level.dat` once and stores the values needed by the UI.
- `PanelMundo` reuses one world metadata snapshot across labels, world settings, and gamerule rendering during a refresh.
- `PanelJugadores` loads whitelist/ban/op counters and known-user synchronization in a `SwingWorker`, then applies the latest result on the EDT.

## Files Changed

- `src/main/java/controlador/WorldDataReader.java`
- `src/main/java/vista/PanelMundo.java`
- `src/main/java/vista/PanelJugadores.java`
- `src/test/java/controlador/WorldDataReaderTest.java`
- `docs/pending-features/server-pages-background-loading-and-metadata-cache.md`

## Verification

- `mvn -q -Dtest=WorldDataReaderTest test`
- `mvn -q "-Dtest=ExtensionMarketplaceDependencyTest,ServerExtensionsServiceTest,WorldDataReaderTest,PanelMundoDebugConnectionsTest" test`
- `mvn -q -DskipTests compile`

All passed. Maven emitted the expected Lombok `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/features/process/server-pages-metadata-cache-background-loading.md`

## Follow-Up Notes

The broader pending feature remains open for full page-level loading placeholders and async loading in `PanelMundo` and `PanelEstadisticas`. This slice reduces repeated metadata parsing and moves player counters off the page-open path without redesigning the right-side page shell.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/world-management-pipeline.md`
- `docs/pipelines/console-and-players-pipeline.md`
