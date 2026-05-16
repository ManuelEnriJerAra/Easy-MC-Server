# Server Pages Metadata Cache And Background Counter Loading Process

## Status

Implemented

## Linked Feature

- `docs/features/server-pages-metadata-cache-background-loading.md`

## Scope

Implement a safe first slice of the pending heavy-page background loading request. This covers shared world metadata snapshots for Mundo and background player-list counters for Jugadores. Full async page placeholders for Mundo and Estadisticas remain tracked in the pending feature note.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked UI, world management, filesystem, and docs workflow guidance. |
| DONE | 2. Inspect heavy page paths | Reviewed `PanelMundo`, `WorldDataReader`, `PanelJugadores`, and `PanelEstadisticas` to identify safe high-impact work. |
| DONE | 3. Add metadata snapshot | Added `WorldDataReader.WorldMetadata` and `readMetadata(...)` for one-pass `level.dat` reads. |
| DONE | 4. Reuse snapshot in Mundo | `PanelMundo` now shares one snapshot across refresh labels, settings, and gamerule rendering. |
| DONE | 5. Move player counters off EDT | `PanelJugadores` now refreshes list counters and known-user sync through `SwingWorker`. |
| DONE | 6. Verify behavior | Ran targeted world/extension tests and compile. |

## Implementation Notes

`WorldDataReader.readMetadata(...)` centralizes values that were previously read through many independent getters. Existing getters remain available for compatibility.

`PanelJugadores` uses a monotonic request sequence so stale counter workers do not overwrite newer server/page state.

## Verification Notes

- `mvn -q -Dtest=WorldDataReaderTest test` passed.
- `mvn -q "-Dtest=ExtensionMarketplaceDependencyTest,ServerExtensionsServiceTest,WorldDataReaderTest,PanelMundoDebugConnectionsTest" test` passed.
- `mvn -q -DskipTests compile` passed.
- Maven emitted the expected Lombok `sun.misc.Unsafe` warning.
