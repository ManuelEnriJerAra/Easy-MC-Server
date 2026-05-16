# README User SEO Refresh

## Status

Implemented

## Feature

The public README now presents Dora as a polished GitHub landing page for a graphical Minecraft server manager, with search-relevant wording, screenshot tables, release installation guidance, user workflows, current feature coverage, reliable section anchors, GPL license details, and a dedicated developer section.

## Motivation

The completed feature note previously described a broad SEO-focused README rewrite, but the actual README still looked like a short developer overview. The repository landing page needed to explain what Dora does, show the app quickly, help users install and start it, and keep developer details available without making them the first thing normal users see.

## Solution

Rewrote `README.md` in English with a complete GitHub README structure: centered hero, badges, product summary, quick links, motivation table, feature table, screenshot gallery tables, release-focused installation guide, quick start, collapsible workflow details, developer source-build commands, repository layout, documentation links, troubleshooting, contribution notes, roadmap references, and GPL-3.0 license status. The feature and workflow sections now include post-merge capabilities such as automation rules, system tray behavior, visual platform selection, conversion backup choice, and snapshot-capable platform creation. The screenshot section uses the available current captures for the home view, creation wizard, automation, worlds, statistics, extension catalog, and themes.

## Files Changed

- `README.md`
- `docs/features/readme-user-seo-refresh.md`
- `docs/features/process/readme-user-seo-refresh.md`

## Verification

- `git diff --check -- README.md docs/features/readme-user-seo-refresh.md docs/features/process/readme-user-seo-refresh.md`
- `Select-String -Path README.md,docs\features\readme-user-seo-refresh.md,docs\features\process\readme-user-seo-refresh.md -Pattern "\s$"`

## Detailed Process

- `docs/features/process/readme-user-seo-refresh.md`

## Follow-Up Notes

Future README updates can add more screenshots once the image files exist. Avoid adding placeholder image links that would render as broken images on GitHub.

## Related Docs

- `docs/features/process/readme-user-seo-refresh.md`
