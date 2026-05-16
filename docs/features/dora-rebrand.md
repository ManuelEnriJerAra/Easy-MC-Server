# Dora Rebrand

## Status

Implemented

## Feature

The application has been rebranded to Dora.

## Motivation

The previous name was too generic. Dora gives the Minecraft server manager a shorter, more distinctive product identity.

## Solution

Updated the product name across the main app surface, README, documentation, Maven metadata, Java config model, resource namespaces, generated app/server metadata names, logs, locks, system properties, and modpack export metadata.

Dora now uses only Dora names for app data and server-local metadata because there are no released installations that need old-name migration.

## Files Changed

- `pom.xml`
- `README.md`
- `AGENTS.md`
- `docs/`
- `src/main/java/controlador/`
- `src/main/java/modelo/DoraConfig.java`
- `src/main/java/vista/`
- `src/main/resources/doraicons/`
- `src/main/resources/doraimages/`
- `src/test/java/`

## Verification

- `mvn -q -DskipTests compile`
- `mvn test`

## Detailed Process

- `docs/features/process/dora-rebrand.md`

## Follow-Up Notes

README screenshots still use the existing versioned image files. Replace them with fresh Dora screenshots when the visual brand assets are finalized.

## Related Docs

- `docs/README.md`
- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/configuration-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
