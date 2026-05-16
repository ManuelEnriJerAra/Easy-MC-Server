# Dora Rebrand Process

## Status

Implemented

## Linked Feature

- `docs/features/dora-rebrand.md`

## Scope

Rename the desktop application to Dora across user-facing copy, build metadata, Java/resource identifiers, app-owned persistence names, and documentation.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Map old-name references | Searched code, resources, tests, README, and docs for previous-brand variants. |
| DONE | 2. Rename app surface | Updated window/tray/startup copy, README/docs, Maven artifact metadata, config class naming, resource paths, internal client properties, thread names, and new generated file names to Dora. |
| DONE | 3. Keep storage Dora-only | Removed old-name migration/fallback handling because the app has not been released to users yet. |
| DONE | 4. Verify behavior | Ran compile and the full test suite after the implementation pass. |

## Implementation Notes

The app writes new data under Dora names such as `~/.dora`, `dora-config.json`, `dora-server-list.json`, `dora-worlds`, and `doraicons`. No old-name migration layer is kept because there are no existing user installations to preserve.

## Verification Notes

Verification performed:

- `mvn -q -DskipTests compile`
- `mvn test`
