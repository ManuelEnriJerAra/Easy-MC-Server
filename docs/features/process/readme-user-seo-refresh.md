# README User SEO Refresh Process

## Status

Implemented

## Linked Feature

- `docs/features/readme-user-seo-refresh.md`

## Scope

Refresh the repository README and project metadata so the public project presentation speaks to end users instead of leading with developer-focused implementation details. The change covers copy, structure, screenshot placeholders, and a short reusable project description. It does not add screenshots themselves or change application behavior.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect existing public docs | Reviewed `README.md`, Maven metadata, existing screenshot assets, and feature documentation standards. |
| DONE | 2. Rewrite README for users | Reworked the README around what users can do with Dora, added an app walkthrough, and kept build details after the user-facing sections. |
| DONE | 3. Add screenshot slots | Added image references for the main areas the user plans to capture, reusing existing screenshots where available. |
| DONE | 4. Update metadata description | Added Maven `name` and `description` fields with a short SEO-oriented Spanish description. |
| DONE | 5. Verify documentation changes | Checked the targeted diff and whitespace for the edited documentation and metadata files. |

## Implementation Notes

The README now starts with a Spanish end-user description using search-relevant terms such as servidor Minecraft, gestor gráfico, consola en tiempo real, mundos, jugadores, configuración, mods, plugins, and modpacks. Developer build instructions remain available, but they are no longer the first user-facing content after the feature list.

Screenshot references use `docs/screenshots/readme/*.png` for future captures and keep the existing `docs/screenshots/0.6.home.png` and `docs/screenshots/0.6.catalog.png` where current assets already exist.

## Verification Notes

- `git diff --check -- README.md pom.xml`
- `Select-String -Path docs\features\readme-user-seo-refresh.md,docs\features\process\readme-user-seo-refresh.md -Pattern "\s$"`
- `mvn -q -DskipTests validate`
