# Conversion Backup Choice Process

## Status

Implemented

## Linked Feature

- `docs/features/conversion-backup-choice.md`

## Scope

Add a backup question to server conversion. Keep backups enabled by default, allow the user to continue without a full durable backup, and preserve existing server files during conversion even when the user declines the full backup.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked server creation, platform adapter, filesystem/path, and feature documentation guidance. |
| DONE | 2. Implement the feature | Added a conversion confirmation result carrying the backup choice and adjusted conversion preservation. |
| DONE | 3. Verify behavior | Added coverage for the no-full-backup path and ran targeted conversion tests plus compile. |

## Implementation Notes

Conversion currently creates a full sibling backup unconditionally and uses that backup as the restore source for preserved config/world/extension data. Making the user-facing backup optional still requires a temporary preservation snapshot when skipped, otherwise platform installers that remove old extension folders could lose user data.

The public UI path now uses `ProcessWizardDialog` for conversion, with arrow navigation from destination platform to a combined compatible-version and backup-choice step. The backup checkbox is shown below the compatible version selector, the combined final step uses the standard rocket finish action, and the checkbox defaults to selected. Existing lower-level conversion callers keep the old behavior through the original overload, while a new overload accepts the explicit backup choice.

## Verification Notes

- `mvn -q "-Dtest=controlador.GestorServidoresTest#convertirServidorExistente_debeCrearBackupYPreservarConfiguracionYMundo+convertirServidorExistente_puedeOmitirBackupCompletoYPreservarDatos+convertirServidorExistente_deModsAPluginsDebePreservarModsYReiniciarCache+convertirServidorExistente_dePluginsAModsDebePreservarPluginsYReiniciarCache" test`
- `mvn -q -DskipTests compile`
- Both commands passed with JDK 25. Maven emitted the expected Lombok `sun.misc.Unsafe` warning.
