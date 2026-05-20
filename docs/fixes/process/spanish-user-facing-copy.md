# Spanish User-Facing Copy Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/spanish-user-facing-copy.md`

## Scope

Correct Spanish spelling, accents, punctuation, and lexical consistency in user-facing application strings. The pass intentionally avoids changing protocol keys, JSON fields, file names, identifiers, and provider URLs unless a previous broad replacement touched them accidentally and needed restoration.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md`, `docs/pipelines/ui-component-pipeline.md`, `docs/pipelines/extensions-pipeline.md`, and `docs/pipelines/server-creation-pipeline.md` for Spanish UI text and affected areas. |
| DONE | 2. Scan Spanish copy | Searched Java sources for common unaccented Spanish terms and agreement-sensitive wording in visible messages. |
| DONE | 3. Correct text | Updated visible strings across server creation, marketplace, extensions, players, worlds, automation, platform validation, and modpack flows. |
| DONE | 4. Restore technical literals | Reverted accidental changes to technical literals such as `Implementation-Version` and event property names. |
| DONE | 5. Verify behavior | Ran Maven compilation without tests. |

## Implementation Notes

Most issues were missing tildes: `esta`, `version`, `extension`, `instalacion`, `seleccion`, `catalogo`, `valido`, `vacia`, and related forms. A few UI strings also needed punctuation, such as the opening question mark in the automation delete confirmation.

The correction pass included a safety review because some words also appear in technical literals. `Implementation-Version` and `automatizacionesServidor` were preserved as technical strings.

## Verification Notes

- `mvn -q -DskipTests compile` passed.
- The normal Lombok/Guice `sun.misc.Unsafe` warning appeared and is not treated as a failure per project guidance.
