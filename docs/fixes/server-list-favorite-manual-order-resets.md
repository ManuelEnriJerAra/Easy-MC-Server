# Server List Favorite Manual Order Resets

## Status

Fixed

## Original Issue

Manually sorting favorite servers did not persist reliably across refreshes or restarts.

## Root Cause

The visual comparator already sorted favorites by `ordenFavorito`, but `GestorServidores.reordenarServidores(...)` only rewrote `ordenLista`. Stale favorite-order metadata could become canonical again during save normalization.

## Solution

`GestorServidores.reordenarServidores(...)` now derives favorite order from the supplied visual order and writes `ordenFavorito` for favorite rows. Non-favorite rows continue to use `ordenLista` as their stable base order.

Regression tests cover favorite order persistence across manager reload and mixed favorite/non-favorite ordering.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `docs/fixes/process/server-list-favorite-manual-order-resets.md`

## Verification

- `mvn -q "-Dtest=GestorServidoresTest,PanelMundoDebugConnectionsTest" test`
- `mvn -q -DskipTests compile`

Both passed. Maven emitted the expected `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/fixes/process/server-list-favorite-manual-order-resets.md`

## Regression Notes

Favorite ordering and base ordering are separate presentation fields. Any future reorder path should update `ordenFavorito` for favorite rows instead of relying on `ordenLista`.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
