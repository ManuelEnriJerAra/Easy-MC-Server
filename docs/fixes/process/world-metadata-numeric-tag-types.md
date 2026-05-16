# World Metadata Numeric Tag Types Process

## Status

Fixed

## Linked Fix

- `docs/fixes/world-metadata-numeric-tag-types.md`

## Scope

Fixes a crash when opening the world page for a `level.dat` whose numeric metadata uses a valid NBT numeric type other than the exact typed getter expected by `WorldDataReader.readMetadata`.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Identify the crashing read | The AWT stack trace pointed to `WorldDataReader.readDifficulty`, where `getIntTag("Difficulty")` attempted to cast a `ByteTag` to `IntTag`. |
| DONE | 2. Generalize numeric reads | Replaced fragile typed metadata reads with a shared `NumberTag` helper so byte, short, int, and long values are accepted where Minecraft stores numeric metadata. |
| DONE | 3. Add regression coverage | Added a `WorldDataReaderTest` case that writes byte, short, and int numeric tags and verifies the metadata snapshot and direct difficulty getter. |
| DONE | 4. Verify behavior | Ran the targeted world reader test and project compilation successfully. |

## Implementation Notes

Minecraft `level.dat` fields are conceptually numeric, but older worlds and different save paths may store fields such as `Difficulty`, `GameType`, booleans, time, seed, or spawn coordinates using different NBT numeric tag widths. `CompoundTag.getIntTag(...)` and similar typed getters cast to an exact tag class and throw when the stored numeric tag has a different width.

The fix keeps the UI-facing Spanish labels unchanged and centralizes tolerant numeric reads in `WorldDataReader.readNumberTag(...)`.

## Verification Notes

- `mvn -q -Dtest=WorldDataReaderTest test`
- `mvn -q -DskipTests compile`
