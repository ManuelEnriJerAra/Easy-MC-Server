# Platform Suffixed Metadata Version Detection Process

## Status

Fixed

## Linked Fix

- `docs/fixes/platform-suffixed-metadata-version-detection.md`

## Scope

Normalize metadata values such as `1.20.1-forge-47.4.0` to the Minecraft version while preserving the stricter generic matcher for unrelated hyphenated text. This fix covers metadata-based detection only and keeps existing jar `version.json` suffix behavior intact.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md`, the platform-adapters pipeline, the pending fix, and solved-fix/process standards. |
| DONE | 2. Implement the fix | Shared the platform-suffix extraction used by jar inspection with metadata normalization. |
| DONE | 3. Verify behavior | Added targeted metadata-only coverage; Maven verification is blocked locally because Maven runs on Java 8 while the project targets Java 25. |

## Implementation Notes

`MinecraftServerJarInspector` already had a platform-suffix-first extraction path. Metadata normalization failed because it only used the stricter generic version matcher, which intentionally rejects matches followed by `-`.

`MinecraftVersionPatterns` now owns the shared version body, strict generic matcher, and known platform-suffix fallback so jar and metadata parsing do not drift.

## Verification Notes

- `git diff --check -- src/main/java/controlador/platform/MinecraftVersionPatterns.java src/main/java/controlador/platform/MinecraftServerJarInspector.java src/main/java/controlador/platform/MinecraftServerVersionDetector.java src/test/java/controlador/platform/ServerPlatformAdaptersTest.java docs/pipelines/platform-adapters-pipeline.md docs/fixes/platform-suffixed-metadata-version-detection.md docs/fixes/process/platform-suffixed-metadata-version-detection.md` passed.
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test` did not run tests because Maven is using Java 8 and fails with `invalid target release: 25`.
- `mvn -q -DskipTests compile` failed for the same Java 8 versus Java 25 environment mismatch.
