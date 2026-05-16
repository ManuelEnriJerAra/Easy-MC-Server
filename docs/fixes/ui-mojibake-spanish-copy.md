# Mojibake In Spanish UI Copy

## Status

Fixed

## Original Issue

Some Spanish UI/controller strings were mojibaked or written as ASCII fallbacks, including broken inverted-question-mark text and clear missing accents in labels, dialogs, tooltips, and warnings.

## Root Cause

Older user-facing literals had been added with inconsistent encoding/copy hygiene. One production prompt kept a mojibaked `¿` prefix, and several nearby UI/controller messages used unaccented Spanish words such as "version", "tamano", "instalacion", and "generacion".

## Solution

Replaced the production mojibake marker and corrected unambiguous user-facing Spanish copy in the affected UI/controller flows. Internal identifiers, property names, and intentional MOTD test fixtures were left unchanged.

## Files Changed

- `src/main/java/controlador/DetectorVersionServidor.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/controlador/MCARenderer.java`
- `src/main/java/controlador/extensions/CurseForgeModpackService.java`
- `src/main/java/controlador/extensions/ServerExtensionsService.java`
- `src/main/java/controlador/platform/FabricMetaClient.java`
- `src/main/java/controlador/platform/ForgeServerPlatformAdapter.java`
- `src/main/java/controlador/platform/ServerPlatformInstallSupport.java`
- `src/main/java/controlador/platform/VanillaServerPlatformAdapter.java`
- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `src/main/java/vista/PanelExtensiones.java`
- `src/main/java/vista/PanelMundo.java`
- `docs/fixes/process/ui-mojibake-spanish-copy.md`
- `docs/fixes/ui-mojibake-spanish-copy.md`

## Verification

- `rg -n "�|�|?" src\main\java`
- `git diff --check`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/ui-mojibake-spanish-copy.md`

## Regression Notes

If mojibake returns, first search production sources for `�`, `�`, and replacement characters, then inspect recent copy changes for files saved with the wrong encoding.

Do not treat `src/test/java/vista/MotdRenderUtilTest.java` `§` strings as regressions unless the MOTD section-code cleanup behavior changes; those strings intentionally exercise malformed MOTD input.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/application-shell-pipeline.md`
