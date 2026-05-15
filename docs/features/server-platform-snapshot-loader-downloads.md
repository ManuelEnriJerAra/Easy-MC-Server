# Server Platform Snapshot Loader Downloads

## Status

Implemented

## Feature

The server creation wizard can now expose snapshot, pre-release, release-candidate, beta, alpha, experimental, and similar unstable Minecraft versions for automated non-Vanilla platform creation when the upstream platform metadata provides those versions.

## Motivation

The wizard already had a snapshots checkbox, but it only behaved meaningfully for Vanilla. Loader and plugin platforms could hide available unstable Minecraft versions even when upstream metadata exposed them.

## Solution

`ServerCreationOption` now carries shared release/snapshot metadata helpers, and the wizard asks each adapter whether unstable creation options are supported before enabling the snapshots checkbox.

Fabric and Quilt map upstream game-version stability flags into release/snapshot creation options. Forge and NeoForge classify by the Minecraft version inferred from the artifact, not by loader/build qualifiers, so beta loader builds for stable Minecraft releases are not treated as Minecraft snapshots. When no stable loader artifact exists yet for a stable Minecraft release, the latest downloadable loader artifact is kept as that release option so the Minecraft version is not hidden. Snapshot-targeted Forge and NeoForge artifacts are listed individually when multiple compatible loader artifacts exist for one Minecraft snapshot/pre-release/RC.

Conversion filtering now compares canonical Minecraft versions instead of raw strings, so Vanilla snapshot names can match loader artifact naming variants such as Forge's older underscore-style pre-release coordinates. Version detection and import now preserve modern semantic snapshot IDs such as `26.2-snapshot-7` instead of truncating them to a release prefix, including jars whose `version.json` has a shorter release-shaped `name` beside the full snapshot `id`, so Fabric conversion can retain the matching loader option. The conversion flow checks compatible target versions before showing the platform picker, disables platform tiles that have no matching build, and gives those disabled tiles a not-available tooltip. The conversion version picker also shows the detected source platform and full Minecraft version before the target version list.

Paper and Purpur classify unstable Minecraft version strings from their project metadata; Paper resolves the exact selected build lazily at install time so version list rendering stays fast.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/controlador/platform/ServerCreationOption.java`
- `src/main/java/controlador/platform/ServerPlatformAdapter.java`
- `src/main/java/controlador/platform/*ServerPlatformAdapter.java`
- `src/main/java/controlador/platform/*MetaClient.java`
- `src/main/java/controlador/platform/*DownloadsClient.java`
- `src/main/java/controlador/platform/*RepositoryClient.java`
- `src/main/java/controlador/platform/VersionStringComparator.java`
- `src/main/java/vista/PlatformSelectorPanel.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `src/test/java/vista/PlatformSelectorPanelTest.java`
- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/platform-adapters-pipeline.md`

## Verification

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

## Detailed Process

- `docs/features/process/server-platform-snapshot-loader-downloads.md`

## Follow-Up Notes

If an upstream API later exposes richer channel metadata in its project-level version list, prefer preserving that metadata during list rendering instead of adding per-version detail calls.

## Related Docs

- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/platform-adapters-pipeline.md`
