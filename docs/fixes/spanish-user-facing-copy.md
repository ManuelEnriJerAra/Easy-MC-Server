# Spanish User-Facing Copy

## Status

Fixed

## Original Issue

Some user-facing Spanish text was semantically or lexically incorrect, mainly because visible messages omitted required accents or punctuation. Examples included forms like `esta`, `version`, `extension`, `instalacion`, `valido`, and confirmations without opening question marks.

## Root Cause

Spanish copy is spread across many Java UI and controller classes, and older strings were added without a consistent accent and punctuation review. Similar words also appear in technical literals, making broad replacements risky.

## Solution

Corrected Spanish copy in visible messages while preserving technical identifiers, JSON keys, event property names, provider URLs, and manifest keys.

## Files Changed

- `src/main/java/controlador`
- `src/main/java/controlador/automation`
- `src/main/java/controlador/extensions`
- `src/main/java/controlador/platform`
- `src/main/java/controlador/world`
- `src/main/java/vista`
- `src/test/java`

## Verification

- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/spanish-user-facing-copy.md`

## Regression Notes

When correcting Spanish copy, do not use broad whole-file replacements for words like `version` or `extension` without reviewing technical literals such as manifest attributes, JSON fields, URLs, event property names, and cache keys.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
