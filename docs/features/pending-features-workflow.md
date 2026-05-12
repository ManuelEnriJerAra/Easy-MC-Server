# Pending Features Workflow

## Status

Implemented

## Feature

Added a dedicated pending-feature notebook for requested features that are not implemented yet.

## Motivation

The repository already separates pending fixes from solved fixes. Feature requests need the same lifecycle: pending while unimplemented, then moved into completed feature notes and process records once implemented.

## Solution

Added `docs/pending-features/README.md` with a standard pending-feature format and workflow. Updated the top-level docs and agent guidance so future feature requests are documented in the correct folder and removed from pending once completed.

## Files Changed

- `AGENTS.md`
- `docs/README.md`
- `docs/pending-features/README.md`
- `docs/features/pending-features-workflow.md`
- `docs/features/process/pending-features-workflow.md`

## Verification

- Documentation review.
- `rg -n "pending-features|docs/features/process/pending-features-workflow.md|docs/features/pending-features-workflow.md" docs AGENTS.md`
- `git diff --check`

## Detailed Process

- `docs/features/process/pending-features-workflow.md`

## Follow-Up Notes

When a requested feature cannot be implemented immediately, create a pending-feature file. When the feature is implemented, transfer the useful context into `docs/features/` and `docs/features/process/`, then delete the pending-feature file.

Do not use pending features for bugs, regressions, or broken behavior; those belong in `docs/pending-fixes/`.

## Related Docs

- `docs/pending-features/README.md`
- `docs/features/README.md`
- `docs/features/process/README.md`
- `docs/pending-fixes/README.md`
