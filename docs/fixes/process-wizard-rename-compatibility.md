# Process Wizard Rename Compatibility

## Status

Fixed

## Original Issue

Opening modpack import could throw `NoClassDefFoundError: vista/WizardDialog$Step` after the reusable wizard was renamed to `ProcessWizardDialog`.

## Root Cause

The code had been renamed, but a stale compiled class still referenced the old nested `WizardDialog.Step` type.

## Solution

Added `vista.WizardDialog` back as a deprecated compatibility facade for stale references to `WizardDialog.show`, `WizardDialog.Step`, and `WizardDialog.Options`. It converts those values into `ProcessWizardDialog` equivalents and delegates execution to the process wizard.

## Files Changed

- `src/main/java/vista/WizardDialog.java`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q '-Dtest=GestorServidoresTest,PanelExtensionesTest,ModrinthModpackServiceTest' test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.

## Detailed Process

- `docs/fixes/process/process-wizard-rename-compatibility.md`

## Regression Notes

When renaming reusable Swing helpers, either clean rebuild every runtime artifact or keep a compatibility facade until old compiled classes cannot be loaded.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
