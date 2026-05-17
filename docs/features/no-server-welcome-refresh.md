# No Server Welcome Refresh

## Status

Implemented

## Feature

The no-server startup frame now presents a modern welcome surface with stronger Dora branding, clearer create/import actions, provisional project description text, and personal/project links.

## Motivation

The original no-server frame was one of the earliest UI surfaces in Dora and no longer matched the rest of the application polish. The startup experience should introduce the project, point users to the creator and repository links, and keep the first server actions easy to find.

## Solution

Updated `NoServerFrame` to use themed rounded surfaces, SVG icons, prominent create/import actions, and link buttons for Ko-fi, GitHub, and LinkedIn. Added an original-color SVG factory path so brand icons can retain their bundled colors when needed.

## Files Changed

- `src/main/java/vista/NoServerFrame.java`
- `src/main/java/vista/SvgIconFactory.java`
- `docs/features/process/no-server-welcome-refresh.md`

## Verification

- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/features/process/no-server-welcome-refresh.md`

## Follow-Up Notes

The placeholder project text is intentionally temporary and should move into the information pane when that pane is expanded with fuller project storytelling.

## Related Docs

- `docs/README.md`
- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
