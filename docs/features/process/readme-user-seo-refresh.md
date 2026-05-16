# README User SEO Refresh Process

## Status

Implemented

## Linked Feature

- `docs/features/readme-user-seo-refresh.md`

## Scope

Refresh the repository README so the public project presentation speaks to end users and improves discoverability for Minecraft server management searches. This covers README copy, structure, screenshot readiness, release-focused installation guidance, common workflows, a separate developer source-build section, and accurate documentation of the completed work. It does not change application behavior or add new screenshots.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect existing public docs | Reviewed `README.md`, screenshot assets, Maven metadata, and the previous feature/process notes. |
| DONE | 2. Identify the mismatch | Confirmed the feature note described an SEO README rewrite that was not actually present in `README.md`. |
| DONE | 3. Review README conventions | Checked current GitHub README guidance for discoverability, screenshots, installation, usage, and linked detailed docs. |
| DONE | 4. Rewrite README for users | Added English user-facing copy, search-relevant Minecraft server terms, a product overview, feature tour, quick start, and common workflows. |
| DONE | 5. Add installation guidance | Added user-facing release-jar guidance while keeping Maven/source-build commands in the developer section. |
| DONE | 6. Separate developer content | Grouped tech stack, build, run, test, repository layout, and docs links under "For Developers" and related sections. |
| DONE | 7. Add support sections | Added troubleshooting, contribution notes, roadmap references, and license status. |
| DONE | 8. Keep image links accurate | Reused only existing screenshots from `docs/screenshots/` so GitHub does not render missing image placeholders. |
| DONE | 9. Update feature documentation | Replaced the stale feature and process notes with the work that was actually completed. |
| DONE | 10. Refresh after master merge | Updated the README feature tour and workflows for automation, system tray behavior, visual platform selection, conversion backup choice, and snapshot-capable creation. |
| DONE | 11. Add new screenshots | Added the current creation wizard, automation, world, statistics, catalog, themes, and refreshed home captures to the README screenshot gallery. |
| DONE | 12. Polish GitHub presentation | Reworked the README with a centered hero, badges, quick links, tables, collapsible workflow sections, explicit anchors, and GPL-3.0 license details. |
| DONE | 13. Verify documentation changes | Checked targeted diffs and trailing whitespace for the edited Markdown files. |

## Implementation Notes

The README now leads with Dora as a graphical Minecraft server manager and includes search-relevant terms such as Minecraft servers, graphical app, live console, worlds, players, settings, mods, plugins, modpacks, Vanilla, Forge, NeoForge, Fabric, Quilt, Paper, and Purpur.

The user walkthrough explains the main flow: create or import a server, choose a platform, select it, start or stop it, use the console, and manage configuration, players, worlds, and extensions.

After the master merge, the README also documents automation rules, system tray background behavior, conversion backup choice, visual platform selection, and snapshot-capable creation options where platform metadata supports them.

Developer content is grouped under "For Developers" so build instructions, source-running notes, project documentation, and tech stack details do not interrupt the normal user overview.

The installation section is honest about the current repository state: it explains how normal users run a release jar if one is available. Maven and source-build commands live in "For Developers" so normal users do not have to read build tooling instructions.

Support content covers Java version checks, import-folder guidance, contribution expectations, roadmap locations, and current license status.

Existing visible screenshots now cover `docs/screenshots/0.6.home.png`, `docs/screenshots/server-creation-wizard.png`, `docs/screenshots/automation.png`, `docs/screenshots/world-panel.png`, `docs/screenshots/stats-panel.png`, `docs/screenshots/0.6.catalog.png`, and `docs/screenshots/themes.png`.

The final README uses GitHub Markdown and light HTML for the centered hero, reliable anchors beside emoji headings, screenshot tables, feature tables, and collapsible workflow sections. The license section now links to the GPL-3.0 `LICENSE` file that is present in the repository.

## Verification Notes

- `git diff --check -- README.md docs/features/readme-user-seo-refresh.md docs/features/process/readme-user-seo-refresh.md`
- `Select-String -Path README.md,docs\features\readme-user-seo-refresh.md,docs\features\process\readme-user-seo-refresh.md -Pattern "\s$"`
