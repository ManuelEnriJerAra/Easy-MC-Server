# Server List Favorite Manual Order Resets Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-list-favorite-manual-order-resets.md`

## Scope

Persist manual favorite ordering separately from base server ordering so favorite rows keep their order across refreshes and restarts.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked application shell, UI component, and models/data guidance plus existing ordering metadata. |
| DONE | 2. Update reorder model | `GestorServidores.reordenarServidores(...)` now rewrites `ordenFavorito` from visual favorite order and keeps `ordenLista` for non-favorite base order. |
| DONE | 3. Add regression tests | Added persistence tests for favorite order and mixed favorite/non-favorite visual order. |
| DONE | 4. Verify behavior | Targeted ordering tests and compile passed. |

## Implementation Notes

The visual comparator already prioritized favorites by `ordenFavorito`; the missing piece was persisting that field during manual drag reorder. Non-favorite rows continue to use `ordenLista`.

## Verification Notes

- `mvn -q "-Dtest=GestorServidoresTest,PanelMundoDebugConnectionsTest" test` passed.
- `mvn -q -DskipTests compile` passed.
