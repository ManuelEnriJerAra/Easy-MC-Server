# Pending Features Workflow Process

## Status

Completed

## Linked Feature

- `docs/features/pending-features-workflow.md`

## Scope

Add a pending-feature documentation workflow parallel to `docs/pending-fixes/`. The requested behavior is that future unimplemented feature requests can be recorded without mixing them into fixes or completed feature notes.

This change only updates project documentation. It does not alter Java code, tests, resources, or build configuration.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read existing notebook standards | Read the feature, feature-process, and pending-fix README files so the new pending-feature folder follows the same repository style. |
| DONE | 2. Define pending-feature format | Added `docs/pending-features/README.md` with scope rules, naming examples, a reusable template, and workflow expectations. |
| DONE | 3. Integrate top-level discovery | Updated `docs/README.md` so `pending-features/README.md` is discoverable with the other work notebooks. |
| DONE | 4. Update agent instructions | Updated `AGENTS.md` so future agents know when to create, complete, and delete pending-feature files. |
| DONE | 5. Record this completed feature | Added the matching completed feature note in `docs/features/` and this detailed process file in `docs/features/process/`. |
| DONE | 6. Verify documentation-only change | Ran link/path searches and `git diff --check`. No Maven compile was needed because this change only affects Markdown documentation. |

## Implementation Notes

The new workflow keeps each notebook role separate:

- `docs/pending-features/` tracks requested but unimplemented features
- `docs/features/` records implemented features
- `docs/features/process/` records detailed implementation steps for completed features
- `docs/pending-fixes/` and `docs/fixes/` remain dedicated to bugs and fixes

Completed features should not stay in `docs/pending-features/`, just as solved fixes should not stay in `docs/pending-fixes/`.

## Verification Notes

- `rg -n "pending-features|docs/features/process/pending-features-workflow.md|docs/features/pending-features-workflow.md" docs AGENTS.md`
- `git diff --check`
