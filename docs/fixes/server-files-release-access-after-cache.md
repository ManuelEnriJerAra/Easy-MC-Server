# Server Files Release Access After Cache

## Status

Fixed

## Original Issue

While the app was open, users could be unable to manually delete installed mod jars after the extensions panel scanned and displayed them. Server files should be read into metadata or display snapshots and released immediately.

## Root Cause

Extension metadata scanning produced pure metadata snapshots, but embedded extension icons were represented as `jar:file:/...!/icon.png` URLs. When `ExtensionIconLoader` loaded those URLs, Java URL caching could keep the underlying jar connection open, leaving the mod jar locked on Windows.

## Solution

`ExtensionIconLoader` now disables URL connection caching before reading icon URLs and decodes cached icon files from byte arrays. A loaded extension icon is kept as an in-memory `ImageIcon`, not as a live connection to the jar.

Extension scans also tolerate a jar disappearing between directory listing and metadata read by evicting stale jar metadata and continuing the refresh.

## Files Changed

- `src/main/java/vista/ExtensionIconLoader.java`
- `src/main/java/controlador/extensions/ServerExtensionsService.java`
- `src/test/java/vista/ExtensionIconLoaderTest.java`
- `src/test/java/controlador/extensions/ServerExtensionsServiceTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q -Dtest=ExtensionIconLoaderTest,ServerExtensionsServiceTest test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.

## Detailed Process

- `docs/fixes/process/server-files-release-access-after-cache.md`

## Regression Notes

Be careful with `jar:file:` URLs, `ImageIO.read(File)`, and any cached object that might lazily read from a server file. Convert file-backed data into byte arrays, strings, records, or `BufferedImage`/`ImageIcon` instances before returning to UI code, and disable URL caches when reading embedded jar resources.

## Related Docs

- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
