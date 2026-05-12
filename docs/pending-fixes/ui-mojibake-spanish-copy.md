# Mojibake In Spanish UI Copy

## Status

Pending

## Area

`vista` and `controlador` user-facing strings.

## Issue

Several Spanish strings appear mojibaked in source and UI-adjacent code, for example broken variants of "Configuracion", "Version", "Tamano", and "Ultima apertura" with garbled accent bytes. This creates inconsistent source readability and can leak broken text into the UI if not already compensated by runtime encoding behavior.

## Desired Behavior

All user-facing Spanish copy should be valid UTF-8 with correct accents, and files should remain consistently encoded.

## Evidence

- `src/main/java/vista/PanelMundo.java`
- `src/main/java/controlador/GestorServidores.java`
- Similar strings may exist across other UI/controller files.

## Suggested Approach

Do a focused encoding/copy cleanup pass, not a broad refactor. Search for common mojibake byte sequences. Replace only user-facing string literals and comments where intent is unambiguous.

Avoid touching generated/binary files or changing line endings across the repo.

## Verification

- `mvn -q -DskipTests compile`
- Manual UI smoke test for affected screens.
- Search again for common mojibake sequences.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/application-shell-pipeline.md`
