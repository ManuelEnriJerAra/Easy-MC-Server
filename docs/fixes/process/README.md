# Fix Process Files

This folder stores detailed step-by-step process notes for completed fixes. Use it as the working checklist while fixing an issue, then keep it as the detailed companion to the solved note in `docs/fixes/`.

Only use this folder for actual fixes. New features, feature planning, broad refactors, and documentation/process-only changes should not receive files here unless they directly resolve a bug, regression, broken behavior, or documented pending fix.

Create one Markdown file per fix. File names should mirror the solved-fix note when possible:

```text
area-short-problem.md
```

## Standard Process Format

```markdown
# Short Fix Process Title

## Status

In Progress

## Linked Fix

- `docs/fixes/area-short-problem.md`

## Scope

What this fix covers, what it intentionally leaves alone, and any relevant user request details.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Summary of what was checked and what constraints were found. |
| IN PROGRESS | 2. Implement the fix | Summary of the current active work. |
| TO DO | 3. Verify behavior | Summary of the planned verification. |

## Implementation Notes

Durable details discovered while working, especially decisions that explain why the final fix took its shape.

## Verification Notes

Commands, tests, manual checks, and any verification that could not be run.
```

## Workflow

When solving a fix:

1. Create the process file before or during the first implementation pass.
2. Keep every planned step in the tracker with exactly one of these status labels: `DONE`, `IN PROGRESS`, or `TO DO`.
3. Update statuses as work progresses so the file reflects the current state if the task is interrupted.
4. Add implementation and verification notes when they are more detailed than the final solved-fix note should be.
5. Link the process file from the matching solved note in `docs/fixes/`.
