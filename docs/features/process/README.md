# Feature Process Files

This folder stores detailed step-by-step process notes for completed features. Use it as the working checklist while implementing a feature, then keep it as the detailed companion to the feature note in `docs/features/`.

Only use this folder for actual features. Fixes, regressions, broken behavior, and cleanup that directly resolves a documented issue belong in `docs/fixes/process/` instead.

Create one Markdown file per feature. File names should mirror the feature note when possible:

```text
area-short-feature.md
```

## Standard Process Format

```markdown
# Short Feature Process Title

## Status

In Progress

## Linked Feature

- `docs/features/area-short-feature.md`

## Scope

What this feature covers, what it intentionally leaves alone, and any relevant user request details.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Summary of what was checked and what constraints were found. |
| IN PROGRESS | 2. Implement the feature | Summary of the current active work. |
| TO DO | 3. Verify behavior | Summary of the planned verification. |

## Implementation Notes

Durable details discovered while working, especially decisions that explain why the final feature took its shape.

## Verification Notes

Commands, tests, manual checks, and any verification that could not be run.
```

## Workflow

When implementing a feature:

1. Create the process file before or during the first implementation pass.
2. Keep every planned step in the tracker with exactly one of these status labels: `DONE`, `IN PROGRESS`, or `TO DO`.
3. Update statuses as work progresses so the file reflects the current state if the task is interrupted.
4. Add implementation and verification notes when they are more detailed than the final feature note should be.
5. Link the process file from the matching feature note in `docs/features/`.
