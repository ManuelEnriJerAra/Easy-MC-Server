# Conversion Backup Choice

## Status

Implemented

## Feature

Server conversion now asks whether to create a full backup before installing the target platform.

## Motivation

Conversion previously created a full backup unconditionally. The user requested an explicit choice during conversion so they can decide whether the durable backup is needed.

## Solution

The conversion flow now uses `ProcessWizardDialog` with arrow navigation for platform selection, then a combined compatible-version and backup-choice step with the rocket finish action. The backup checkbox sits below the compatible version selector, is selected by default, and the selected value is passed into conversion. Existing lower-level callers keep the old default behavior through the original overload.

When the user skips the full backup, conversion still creates a temporary preservation snapshot for config files, worlds, and extension folders. That temporary snapshot is used only to restore user data after installation and is deleted afterward.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.GestorServidoresTest#convertirServidorExistente_debeCrearBackupYPreservarConfiguracionYMundo+convertirServidorExistente_puedeOmitirBackupCompletoYPreservarDatos+convertirServidorExistente_deModsAPluginsDebePreservarModsYReiniciarCache+convertirServidorExistente_dePluginsAModsDebePreservarPluginsYReiniciarCache" test`
- `mvn -q -DskipTests compile`

Both commands passed with JDK 25. Maven emitted the expected Lombok `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/features/process/conversion-backup-choice.md`

## Follow-Up Notes

If conversion is later reorganized, keep the backup choice visible in the wizard before the rocket action and retain the temporary preservation snapshot for no-backup conversions.

## Related Docs

- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
