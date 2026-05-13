# Process Wizard Rename Compatibility Process

## Status

Completed

## Linked Fix

- `docs/fixes/process-wizard-rename-compatibility.md`

## Scope

Fix the runtime `NoClassDefFoundError: vista/WizardDialog$Step` reported after renaming the reusable wizard shell to `ProcessWizardDialog`.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect stack trace | The AWT stack showed `PanelExtensiones` loading an old `WizardDialog$Step` symbol at runtime. |
| DONE | 2. Add compatibility shim | Restored `vista.WizardDialog` as a deprecated facade that delegates to `ProcessWizardDialog`. |
| DONE | 3. Verify compile and tests | Recompiled and ran targeted modpack/panel tests. |

## Implementation Notes

The current source uses `ProcessWizardDialog`, but stale compiled classes or partial rebuilds may still reference `WizardDialog$Step`. Keeping a small facade prevents those stale references from crashing while preserving the process-wizard naming for new code.

## Verification Notes

- `mvn -q -DskipTests compile`
- `mvn -q '-Dtest=GestorServidoresTest,PanelExtensionesTest,ModrinthModpackServiceTest' test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot`.
