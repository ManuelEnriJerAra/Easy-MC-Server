# Feature Process Documentation

## Status

Implemented

## Feature

Added a dedicated feature documentation notebook with detailed process files, parallel to the existing fixes notebook.

## Motivation

Feature work needs the same durable process history as fixes, but features should not be recorded in `docs/fixes/`.

## Solution

Added `docs/features/` for completed feature notes and `docs/features/process/` for detailed step-by-step feature process trackers. Updated the top-level docs and agent guidance so future feature work uses the new structure.

## Files Changed

- `AGENTS.md`
- `docs/README.md`
- `docs/features/README.md`
- `docs/features/process/README.md`
- `docs/features/feature-process-documentation.md`
- `docs/features/process/feature-process-documentation.md`

## Verification

- Documentation review.
- `git status --short`
- Targeted `rg` checks for the new feature docs paths.

## Detailed Process

- `docs/features/process/feature-process-documentation.md`

## Follow-Up Notes

When future feature work is implemented, keep the feature note concise and put the detailed implementation trail in the linked process file.

Do not move fixes, regressions, or resolved pending issues into `docs/features/`; those remain in `docs/fixes/` and `docs/fixes/process/`.

## Related Docs

- `docs/features/README.md`
- `docs/features/process/README.md`
- `docs/fixes/README.md`
- `docs/fixes/process/README.md`
