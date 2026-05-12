# Mojibake In Spanish UI Copy Process

## Status

Completed

## Linked Fix

- `docs/fixes/ui-mojibake-spanish-copy.md`

## Scope

Resolve the former pending Spanish UI copy mojibake issue. The cleanup is intentionally focused on unambiguous user-facing strings in `vista` and `controlador` code, especially strings with mojibake bytes or clear missing Spanish accents called out by the original issue report.

This does not rename Java identifiers, change persisted property keys, alter binary/generated files, or convert intentionally escaped protocol text such as Minecraft MOTD section-code test fixtures.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read repository guidance | Read `docs/README.md`, `docs/pipelines/ui-component-pipeline.md`, `docs/pipelines/application-shell-pipeline.md`, and the pending-fix note to keep the cleanup focused on UI/controller copy. |
| DONE | 2. Search for mojibake markers | Searched source for `Ã`, `Â`, and replacement characters. Found one production mojibake string in `PanelExtensiones`; `MotdRenderUtilTest` keeps `Â§` intentionally for MOTD cleanup coverage. |
| DONE | 3. Patch user-facing Spanish copy | Replaced the production mojibake prompt and corrected clear user-facing Spanish accents in world preview, extension compatibility, marketplace, server conversion, platform installation, and related error text. |
| DONE | 4. Re-scan for mojibake markers | Re-ran the marker search against `src/main/java`; no production `Ã`, `Â`, or replacement-character matches remain. |
| DONE | 5. Verify compile | Ran `mvn -q -DskipTests compile` successfully after the source cleanup. |
| DONE | 6. Document the completed fix | Added a solved note in `docs/fixes/`, kept the detailed process here, and removed the issue from `docs/pending-fixes/` because solved fixes do not remain pending. |

## Implementation Notes

The cleanup preferred correct UTF-8 text over ASCII fallbacks for labels, dialogs, tooltips, and user-visible warnings. Internal identifiers such as `GestorConfiguracion`, `formatearTamano`, and property names were left unchanged.

One functional follow-up was needed in `ExtensionMarketplaceDialog`: friendly error matching now accepts both the older unaccented text and the corrected accented text for already-installed and invalid-download cases.

## Verification Notes

- `rg -n "Ã|Â|�" src\main\java`
- `git diff --check`
- `mvn -q -DskipTests compile`
