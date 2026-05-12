# Features

This folder stores completed feature notes. Treat it as the feature-side memory bank for product additions, behavior expansions, and documentation/process features that are not bug fixes.

Only document features here. Bug fixes, regressions, broken behavior, risky inconsistencies, or cleanup that directly resolves a documented issue belong in `docs/fixes/` instead.

Use one Markdown file per completed feature. File names should be easy to recognize:

```text
area-short-feature.md
```

Detailed step-by-step process notes for features live in `process/`. Create one process file per implemented feature and link it from the feature note.

## Standard Feature Format

```markdown
# Short Feature Title

## Status

Implemented

## Feature

What was added.

## Motivation

Why the feature was requested or useful.

## Solution

What changed and where.

## Files Changed

- `path/to/file.java`

## Verification

Commands, tests, or manual checks performed.

## Detailed Process

- `docs/features/process/area-short-feature.md`

## Follow-Up Notes

What to watch for if this feature is expanded later.

## Related Docs

Relevant files in `docs/pipelines/`, `docs/features/`, or other permanent docs.
```

## Workflow

When a feature is implemented:

1. Create or update a detailed process file in `process/` while working.
2. Keep the process file's step tracker current with `DONE`, `IN PROGRESS`, and `TO DO` labels.
3. Add a feature note here using the standard format.
4. Mention the user-facing purpose and the durable implementation lesson.
5. Link the detailed process file and related docs.
6. Keep code snippets short; prefer file/class/method references.
7. If the feature changes a pipeline, update the matching file in `docs/pipelines/`.
