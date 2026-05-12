# Pending Features

This folder tracks requested features, product additions, behavior expansions, and documentation/process improvements that are not implemented yet.

Only unimplemented feature requests belong here. When a feature is completed, move all relevant context into `docs/features/` and `docs/features/process/`, then delete the pending-feature file.

Use one Markdown file per requested feature. File names should be easy to recognize:

```text
area-short-feature.md
```

Examples:

```text
world-preview-export-options.md
extensions-marketplace-bulk-actions.md
docs-pending-features-workflow.md
```

## Standard Pending-Feature Format

```markdown
# Short Feature Title

## Status

Pending

## Area

Main package/class/component or documentation area involved.

## Feature Request

What should be added or expanded.

## Motivation

Why this feature is useful.

## Desired Behavior

What the program or docs should do when the feature exists.

## Notes

- User request details.
- Existing file/class/doc references.
- Constraints or design expectations.

## Suggested Approach

Practical direction for a future implementation. Keep it scoped.

## Verification

Compile/tests/manual checks that should be run when implementing.

## Related Docs

Relevant files in `docs/pipelines/`, `docs/features/`, or other permanent docs.
```

## Workflow

When the user requests a feature that is not completed immediately:

1. Create or update a pending-feature file using the standard format.
2. Keep the request concrete: requested behavior, motivation, constraints, and likely affected area.
3. Link relevant project docs from `docs/pipelines/` or permanent feature docs.
4. When implemented, move the learning into `docs/features/` as a completed feature note and keep detailed steps in `docs/features/process/`.
5. Delete the pending-feature file after the feature is implemented, verified, and documented. Do not keep implemented features in this folder.
