# Fixes

This folder stores solved issue notes. Treat it as a practical memory bank for recurring bug shapes and implementation patterns.

Only document fixes here: bug fixes, regressions, broken behavior, risky inconsistencies, or cleanup that directly resolves a documented issue. Do not add new features, feature planning, or general documentation/process changes to this folder unless they are part of resolving a specific fix.

Use one Markdown file per solved issue. File names should be easy to recognize and usually mirror the original pending-fix name:

```text
area-short-problem.md
```

Detailed step-by-step process notes for fixes live in `process/`. Create one process file per solved fix and link it from the solved note.

## Standard Solved-Issue Format

```markdown
# Short Fix Title

## Status

Fixed

## Original Issue

What broke or behaved poorly.

## Root Cause

Why it happened.

## Solution

What changed and where.

## Files Changed

- `path/to/file.java`

## Verification

Commands, tests, or manual checks performed.

## Detailed Process

- `docs/fixes/process/area-short-problem.md`

## Regression Notes

What to watch for if a similar issue returns.

## Related Docs

Relevant files in `docs/pipelines/`. Do not link deleted pending-fix files.
```

## Workflow

When an issue is solved:

1. Create or update a detailed process file in `process/` while working.
2. Keep the process file's step tracker current with `DONE`, `IN PROGRESS`, and `TO DO` labels.
3. Add a solved note here using the standard format.
4. Mention the exact root cause and the durable lesson.
5. Link the detailed process file and related docs.
6. Transfer relevant information from the pending-fix file into the solved note and process file, then delete the pending-fix file.
7. Keep code snippets short; prefer file/class/method references.
8. If the fix changes a pipeline, update the matching file in `docs/pipelines/`.
