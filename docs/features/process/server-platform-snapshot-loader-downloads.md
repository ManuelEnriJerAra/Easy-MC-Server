# Server Platform Snapshot Loader Downloads Process

## Status

Implemented

## Linked Feature

- `docs/features/server-platform-snapshot-loader-downloads.md`

## Scope

Implement snapshot and unstable Minecraft version creation options for automated non-Vanilla platforms when upstream metadata exposes them. Keep list rendering efficient by avoiding per-version build lookups for Paper and Purpur, and leave unsupported automated creation platforms unchanged.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md`, the server creation pipeline, the platform adapters pipeline, and the pending feature request. |
| DONE | 2. Generalize wizard filtering | Used `ServerPlatformAdapter.supportsUnstableCreationOptions()` so the server creation wizard can apply the snapshots checkbox beyond Vanilla. |
| DONE | 3. Map platform metadata | Populated `ServerCreationOption.versionType` from Fabric/Quilt game-version stability flags and from Minecraft version strings for Forge, NeoForge, Paper, and Purpur. |
| DONE | 4. Preserve compatible snapshot loader choices | Kept every Forge/NeoForge artifact that targets a Minecraft snapshot/pre-release/RC instead of collapsing those options to one latest artifact. |
| DONE | 5. Preserve lazy downloads | Kept Paper and Purpur list rendering to project/version metadata only, resolving concrete Paper build URLs only for the selected install option. |
| DONE | 6. Support snapshot conversion | Accepted modern semantic snapshot IDs during detection/persistence, preferred full snapshot IDs over shorter release-shaped names in `version.json`, compared conversion target options through canonical Minecraft-version compatibility, and disabled target platform tiles that have no compatible build. |
| DONE | 7. Verify behavior | Added focused adapter/conversion coverage and ran the targeted platform suite plus compile. |

## Implementation Notes

`ServerCreationOption` now provides shared release/snapshot constants and helpers for stability booleans and text-derived unstable markers. The text helper recognizes snapshot, alpha, beta, experimental, dev, `preN`, `rcN`, and Mojang weekly snapshot IDs.

Fabric and Quilt use upstream `stable` flags from game-version metadata. Forge and NeoForge classify creation options by the inferred Minecraft version, not by loader/build qualifiers such as beta, so the snapshots checkbox means "server loader for a Minecraft snapshot/pre-release/RC" rather than "unstable loader build." For stable Minecraft releases, unstable loader artifacts are not surfaced as snapshot options. They are used as release fallbacks only when no stable loader exists yet for that Minecraft release.

Forge snapshot Minecraft versions can appear in Maven artifact coordinates with naming variants such as `1.7.10_pre4-*`; the app canonicalizes those names to match Vanilla-style snapshot versions during conversion while keeping the exact artifact as `platformVersion` for installer downloads. Snapshot-targeted Forge and NeoForge artifacts are listed individually because a single Minecraft snapshot may have multiple compatible loader artifacts.

NeoForge newer snapshot-targeted artifacts can encode the Minecraft target in build metadata, for example `+snapshot-1` or `+pre-3`. Those metadata markers are parsed as Minecraft snapshot/pre-release targets instead of being dropped as unstable loader builds for a stable-looking base version.

Modern semantic snapshot IDs such as `26.2-snapshot-7` must remain intact through jar inspection, metadata-file detection, import, and conversion. The app previously truncated those values to `26.2`, which prevented Fabric's matching `26.2-snapshot-7` option from being retained during conversion. Jar inspection now prefers the full Minecraft `id` when a `version.json` also contains a shorter release-shaped `name`. The conversion flow now loads compatible target builds before showing platform tiles, disables tiles that have no matching build for the current Minecraft version, and displays the detected source platform plus the full Minecraft version before listing compatible target builds.

Paper uses `latest-stable` for release-shaped Minecraft versions and `latest-unstable` for unstable Minecraft versions in the creation list. If a release-shaped Minecraft version has no stable Paper build yet, install falls back to the latest downloadable Paper build instead of failing after the user has already selected the option. The concrete build ID and download URL are still resolved during installation so the wizard does not call one builds endpoint per version. Purpur keeps the upstream `latest` alias and classifies unstable version strings if the project endpoint exposes them.

## Verification Notes

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#purpurCreationOptions_debenEvitarConsultasPorVersion" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#forgeCreationOptions_debenSepararBuildsEstablesEInestables+neoForgeCreationOptions_debenIgnorarBuildsInestablesDeLoaderParaReleasesMinecraft" test`
- `mvn -q "-Dtest=controlador.GestorServidoresTest#filtrosCreacionVersiones_debenMostrarReleasesYSnapshotsCuandoAmbosEstanActivos" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#forgeCreationOptions_debenSepararBuildsEstablesEInestables+neoForgeCreationOptions_debenIgnorarBuildsInestablesDeLoaderParaReleasesMinecraft+creationClients_debenParsearOpcionesYDirectorios,controlador.GestorServidoresTest#cargarOpcionesConversion_debeEmparejarSnapshotsVanillaConVersionCanonicaDelLoader+filtrosCreacionVersiones_debenMostrarReleasesYSnapshotsCuandoAmbosEstanActivos" test`
- `mvn -q "-Dtest=controlador.GestorServidoresTest#importarServidorDebeAceptarSnapshotSemanticoFuturoDesdeVersionJson+cargarOpcionesConversion_debeEmparejarSnapshotModernoConFabric+cargarOpcionesConversion_debeEmparejarSnapshotsVanillaConVersionCanonicaDelLoader,controlador.platform.ServerPlatformAdaptersTest#creationClients_debenParsearOpcionesYDirectorios" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#detect_debeInferirVersionSnapshotSemanticaDesdeJar,controlador.GestorServidoresTest#importarServidorDebeAceptarSnapshotSemanticoFuturoDesdeVersionJson+cargarOpcionesConversion_debeEmparejarSnapshotModernoConFabric" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest,controlador.GestorServidoresTest#importarServidorDebeAceptarSnapshotSemanticoFuturoDesdeVersionJson+cargarOpcionesConversion_debeEmparejarSnapshotModernoConFabric+cargarOpcionesConversion_debeEmparejarSnapshotsVanillaConVersionCanonicaDelLoader+filtrosCreacionVersiones_debenMostrarReleasesYSnapshotsCuandoAmbosEstanActivos" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#detect_debeInferirVersionSnapshotSemanticaDesdeJar+detect_debePreferirIdSnapshotSobreNombreReleaseDesdeJar+creationClients_debenParsearOpcionesYDirectorios,controlador.GestorServidoresTest#versionesMinecraftCanonicas_debenNormalizarSnapshotsPreYRcModernos+importarServidorDebeAceptarSnapshotSemanticoFuturoDesdeVersionJson+importarServidorDebePreferirIdSnapshotSobreNombreReleaseDesdeVersionJson+resolverOrigenConversionDebeActualizarSnapshotTruncadoDesdeJar+cargarOpcionesConversion_debeEmparejarSnapshotModernoConFabric+cargarOpcionesConversion_debeEmparejarSnapshotsVanillaConVersionCanonicaDelLoader+filtrosCreacionVersiones_debenMostrarReleasesYSnapshotsCuandoAmbosEstanActivos" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest,controlador.GestorServidoresTest#versionesMinecraftCanonicas_debenNormalizarSnapshotsPreYRcModernos+importarServidorDebeAceptarSnapshotSemanticoFuturoDesdeVersionJson+importarServidorDebePreferirIdSnapshotSobreNombreReleaseDesdeVersionJson+resolverOrigenConversionDebeActualizarSnapshotTruncadoDesdeJar+cargarOpcionesConversion_debeEmparejarSnapshotModernoConFabric+cargarOpcionesConversion_debeEmparejarSnapshotsVanillaConVersionCanonicaDelLoader+filtrosCreacionVersiones_debenMostrarReleasesYSnapshotsCuandoAmbosEstanActivos" test`
- `mvn -q "-Dtest=controlador.GestorServidoresTest#evaluarDisponibilidadConversion_debeMarcarPlataformasSinVersionCompatibleComoNoDisponibles+cargarOpcionesConversion_debeEmparejarSnapshotModernoConFabric+cargarOpcionesConversion_debeEmparejarSnapshotsVanillaConVersionCanonicaDelLoader+resolverOrigenConversionDebeActualizarSnapshotTruncadoDesdeJar,controlador.platform.ServerPlatformAdaptersTest#detect_debePreferirIdSnapshotSobreNombreReleaseDesdeJar" test`
- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q -DskipTests compile`
