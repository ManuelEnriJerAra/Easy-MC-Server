# World Metadata Numeric Tag Types

## Status

Fixed

## Original Issue

Opening the world page could crash the AWT event thread when `level.dat` stored `Difficulty` as a `ByteTag` instead of an `IntTag`.

## Root Cause

The metadata snapshot reader used exact NBT typed getters such as `getIntTag("Difficulty")`. The Querz NBT API casts these to a specific tag class, so valid numeric variants like `ByteTag` caused `ClassCastException`.

## Solution

`WorldDataReader` now reads world metadata numeric fields through a shared `NumberTag` helper and formats game mode/difficulty from the numeric value. The same tolerant path covers metadata snapshot fields and direct public getters that previously used exact numeric tag getters.

## Files Changed

- `src/main/java/controlador/WorldDataReader.java`
- `src/test/java/controlador/WorldDataReaderTest.java`

## Verification

- `mvn -q -Dtest=WorldDataReaderTest test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/world-metadata-numeric-tag-types.md`

## Regression Notes

When reading `level.dat`, prefer `NumberTag` for numeric world metadata unless the exact NBT tag width is part of the behavior being tested. Minecraft save data can preserve the same logical field with different numeric tag widths.

## Related Docs

- `docs/pipelines/world-management-pipeline.md`
