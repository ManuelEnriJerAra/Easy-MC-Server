# Pending Fixes

This folder tracks known issues, inconsistencies, and cleanup candidates that are not solved yet.

Only unresolved issues belong here. When a fix is completed, move all relevant context into `docs/fixes/` and `docs/fixes/process/`, then delete the pending-fix file.

Use one Markdown file per issue. File names should be easy to recognize:

```text
area-short-problem.md
```

Examples:

```text
ui-mojibake-spanish-copy.md
extensions-large-dialog-responsibilities.md
world-preview-worker-state.md
```

## Standard Issue Format

```markdown
# Short Issue Title

## Status

Pending

## Area

Main package/class/component involved.

## Issue

What is wrong, inconsistent, risky, duplicated, or hard to maintain.

## Desired Behavior

What the program should do instead.

## Evidence

- File/class/method references.
- Symptoms seen in UI or tests.
- Notes from project lookup.

## Suggested Approach

Practical direction for a future fix. Keep it scoped.

## Verification

Compile/tests/manual checks that should be run when fixing.

## Related Docs

Relevant files in `docs/pipelines/`.
```

## Workflow

When the user reports a bug:

1. Create or update a pending-fix file using the standard format.
2. Keep the issue concrete: current behavior vs desired behavior.
3. Link relevant project docs from `docs/pipelines/`.
4. When fixed, move the learning into `docs/fixes/` as a solved-issue note and keep detailed steps in `docs/fixes/process/`.
5. Delete the pending-fix file after the fix is implemented, verified, and documented. Do not keep resolved issues in this folder.
