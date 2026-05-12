# Feature Process Documentation Process

## Status

Completed

## Linked Feature

- `docs/features/feature-process-documentation.md`

## Scope

Add a permanent documentation workflow for feature process files, parallel to the existing fix process workflow. The requested behavior is that future feature work can use process files and keep its completed information recorded outside `docs/fixes/`.

This change only updates project documentation. It does not alter Java code, tests, resources, build configuration, or existing fix history.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect existing fix workflow | Read `docs/fixes/README.md`, `docs/fixes/process/README.md`, `docs/README.md`, and `AGENTS.md` so the feature workflow mirrors the established fix structure without mixing features into fixes. |
| DONE | 2. Define the feature-note standard | Added `docs/features/README.md` with scope boundaries, naming guidance, a reusable feature-note template, and workflow expectations. |
| DONE | 3. Define the feature-process standard | Added `docs/features/process/README.md` with the step tracker format and exact `DONE`, `IN PROGRESS`, and `TO DO` status labels. |
| DONE | 4. Create this feature's records | Added this process file and the matching feature note so the new workflow is used immediately by the feature that introduced it. |
| DONE | 5. Integrate discovery docs | Updated `docs/README.md` so agents can find the feature and feature-process notebooks. |
| DONE | 6. Update agent instructions | Updated `AGENTS.md` so future coding agents know where to document features and where not to document them. |
| DONE | 7. Verify documentation-only change | Reviewed links and ran status/diff checks. No Maven compile was needed because this change only affects Markdown documentation. |

## Implementation Notes

The feature workflow intentionally mirrors the fix workflow while keeping the storage paths distinct:

- features use `docs/features/` and `docs/features/process/`
- fixes use `docs/fixes/` and `docs/fixes/process/`
- unresolved issues use `docs/pending-fixes/`

This keeps feature history permanent without weakening the rule that `docs/fixes/` only contains actual fixes.

## Verification Notes

- Reviewed the Markdown additions for consistent paths and status labels.
- Ran `git status --short` and targeted `rg` checks for the new paths.
- Skipped Maven commands because this change only affects documentation.
