# Fixes

This folder stores solved issue notes. Treat it as a practical memory bank for recurring bug shapes and implementation patterns.

Use one Markdown file per solved issue. File names should be easy to recognize and usually mirror the original pending-fix name:

```text
area-short-problem.md
```

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

## Regression Notes

What to watch for if a similar issue returns.

## Related Docs

Relevant files in `docs/pipelines/` or `docs/pending-fixes/`.
```

## Workflow

When an issue is solved:

1. Add a solved note here using the standard format.
2. Mention the exact root cause and the durable lesson.
3. Link the related docs and pending issue if one existed.
4. Keep code snippets short; prefer file/class/method references.
5. If the fix changes a pipeline, update the matching file in `docs/pipelines/`.
