# Platform Suffixed Metadata Version Detection

## Status

Fixed

## Original Issue

Metadata-based server version detection could miss Minecraft versions when a metadata value included a known platform suffix, for example `1.20.1-forge-47.4.0`.

## Root Cause

`MinecraftServerVersionDetector.normalizeVersion(...)` only used the strict generic Minecraft-version matcher. That matcher correctly rejects matches followed by `-`, but metadata normalization did not have the platform-suffix fallback already used by jar `version.json` parsing.

## Solution

Shared Minecraft-version extraction in `MinecraftVersionPatterns` and routed both jar inspection and metadata normalization through it. The extractor first accepts known platform suffixes, then falls back to the strict generic matcher so unrelated hyphenated values remain rejected.

## Files Changed

- `src/main/java/controlador/platform/MinecraftVersionPatterns.java`
- `src/main/java/controlador/platform/MinecraftServerJarInspector.java`
- `src/main/java/controlador/platform/MinecraftServerVersionDetector.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `docs/pipelines/platform-adapters-pipeline.md`

## Verification

- `git diff --check -- src/main/java/controlador/platform/MinecraftVersionPatterns.java src/main/java/controlador/platform/MinecraftServerJarInspector.java src/main/java/controlador/platform/MinecraftServerVersionDetector.java src/test/java/controlador/platform/ServerPlatformAdaptersTest.java docs/pipelines/platform-adapters-pipeline.md docs/fixes/platform-suffixed-metadata-version-detection.md docs/fixes/process/platform-suffixed-metadata-version-detection.md`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test` could not run because Maven is using Java 8 and the project targets Java 25.
- `mvn -q -DskipTests compile` could not run for the same local Java 8 target-release mismatch.

## Detailed Process

- `docs/fixes/process/platform-suffixed-metadata-version-detection.md`

## Regression Notes

Keep metadata hint parsing and jar `version.json` parsing on the same extraction helper. Add known platform suffixes there if another platform starts writing suffixed Minecraft version IDs.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
