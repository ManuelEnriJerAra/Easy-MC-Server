# Quilt Loader Stable Default Process

## Status

Fixed

## Linked Fix

- `docs/fixes/quilt-loader-stable-default.md`

## Scope

Avoid using a beta Quilt loader as the default loader for release-filtered server creation options when a stable loader is available.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Verify live metadata | Confirmed Quilt loader metadata currently lists beta loaders before the latest stable loader. |
| DONE | 2. Implement stable selection | Changed Quilt metadata selection to prefer the first release-looking loader version, with fallback to the first version only if no stable-looking loader exists. |
| DONE | 3. Verify behavior | Added fixture coverage with a beta loader before a stable loader and ran targeted platform tests. |

## Implementation Notes

Quilt game-version stability still drives `ServerCreationOption.versionType`. Loader beta status should not silently affect release-filtered defaults while a stable loader is available.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#detect_debeInferirSnapshotForgeDesdeCoordenadaRuntime+detect_debeInferirSnapshotNeoForgeDesdeCoordenadaRuntime+creationClients_debenParsearOpcionesYDirectorios" test`
