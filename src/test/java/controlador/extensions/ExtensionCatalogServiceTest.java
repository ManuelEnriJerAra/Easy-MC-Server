package controlador.extensions;

import modelo.Server;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionCatalogServiceTest {
    @Test
    void shouldExposeDefaultProviders() {
        ExtensionCatalogService service = new ExtensionCatalogService();

        List<ExtensionCatalogProviderDescriptor> providers = service.getAvailableProviders();

        assertThat(providers).extracting(ExtensionCatalogProviderDescriptor::providerId)
                .containsExactly("modrinth", "curseforge", "hangar");
        assertThat(providers)
                .filteredOn(provider -> provider.providerId().equals("modrinth"))
                .singleElement()
                .satisfies(provider -> {
                    assertThat(provider.capabilities()).contains(
                            ExtensionCatalogCapability.SEARCH,
                            ExtensionCatalogCapability.DETAILS,
                            ExtensionCatalogCapability.DOWNLOAD,
                            ExtensionCatalogCapability.UPDATES
                    );
                    assertThat(provider.supportedExtensionTypes()).contains(ServerExtensionType.MOD, ServerExtensionType.PLUGIN);
                    assertThat(provider.supportedPlatforms()).contains(ServerPlatform.FABRIC, ServerPlatform.PAPER);
                });
        assertThat(providers)
                .filteredOn(provider -> provider.providerId().equals("hangar"))
                .singleElement()
                .satisfies(provider -> {
                    assertThat(provider.capabilities()).contains(ExtensionCatalogCapability.SEARCH, ExtensionCatalogCapability.DOWNLOAD);
                    assertThat(provider.supportedExtensionTypes()).containsExactly(ServerExtensionType.PLUGIN);
                    assertThat(provider.supportedPlatforms()).contains(ServerPlatform.PAPER);
                });
        assertThat(providers)
                .filteredOn(provider -> provider.providerId().equals("curseforge"))
                .singleElement()
                .satisfies(provider -> {
                    assertThat(provider.capabilities()).isEmpty();
                    assertThat(provider.limitations()).contains("no implementado");
                });
    }

    @Test
    void curseForgeCatalogShouldNotExposeStubResultsAsRealSupport() throws IOException {
        CurseForgeExtensionCatalogProvider provider = new CurseForgeExtensionCatalogProvider("test-key");
        Server server = new Server();
        server.setPlatform(ServerPlatform.FORGE);
        server.setVersion("1.21.1");

        assertThat(provider.supportsSearch()).isFalse();
        assertThat(provider.search(new ExtensionCatalogQuery("jei", ServerPlatform.FORGE, ServerExtensionType.MOD, "1.21.1", 10))).isEmpty();
        assertThat(provider.resolveDownload("jei", null, server)).isEmpty();
    }

    @Test
    void shouldAggregateSearchWithoutBindingToConcreteProvider() throws IOException {
        ExtensionCatalogProvider hangar = new FakeProvider(
                "hangar",
                "Hangar",
                ExtensionSourceType.HANGAR,
                List.of(new ExtensionCatalogEntry(
                        "hangar",
                        "31",
                        null,
                        "ViaVersion",
                        "ViaVersion",
                        null,
                        "Compatibilidad de protocolos",
                        ExtensionSourceType.HANGAR,
                        ServerExtensionType.PLUGIN,
                        Set.of(ServerPlatform.PAPER),
                        Set.of("1.21.1"),
                        null,
                        "https://hangar.papermc.io/ViaVersion/ViaVersion",
                        null,
                        0L
                )),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
        ExtensionCatalogProvider modrinth = new FakeProvider(
                "modrinth",
                "Modrinth",
                ExtensionSourceType.MODRINTH,
                List.of(new ExtensionCatalogEntry(
                        "modrinth",
                        "abc",
                        "v1",
                        "Geyser",
                        "GeyserMC",
                        "1.0.0",
                        "Puente Bedrock",
                        ExtensionSourceType.MODRINTH,
                        ServerExtensionType.PLUGIN,
                        Set.of(ServerPlatform.PAPER),
                        Set.of("1.21.1"),
                        null,
                        "https://modrinth.com/plugin/geyser",
                        null,
                        0L
                )),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
        ExtensionCatalogService service = new ExtensionCatalogService(
                new ExtensionCatalogRegistry(List.of(modrinth, hangar))
        );

        List<ExtensionCatalogEntry> results = service.search(new ExtensionCatalogQuery(
                "version",
                ServerPlatform.PAPER,
                ServerExtensionType.PLUGIN,
                "1.21.1",
                10
        ));

        assertThat(results).extracting(ExtensionCatalogEntry::providerId)
                .containsExactly("hangar", "modrinth");
        assertThat(results).extracting(ExtensionCatalogEntry::displayName)
                .containsExactly("ViaVersion", "Geyser");
    }

    @Test
    void shouldFilterWrongEcosystemCatalogResultsEvenWhenProviderReturnsThem() throws IOException {
        ExtensionCatalogProvider provider = new FakeProvider(
                "mixed",
                "Mixed",
                ExtensionSourceType.MODRINTH,
                List.of(
                        new ExtensionCatalogEntry(
                                "mixed",
                                "plugin",
                                "p1",
                                "Wrong Plugin",
                                "Example",
                                "1.0.0",
                                "Plugin result",
                                ExtensionSourceType.MODRINTH,
                                ServerExtensionType.PLUGIN,
                                Set.of(ServerPlatform.PAPER),
                                Set.of("1.21.1"),
                                null,
                                null,
                                null,
                                0L
                        ),
                        new ExtensionCatalogEntry(
                                "mixed",
                                "mod",
                                "m1",
                                "Right Mod",
                                "Example",
                                "1.0.0",
                                "Mod result",
                                ExtensionSourceType.MODRINTH,
                                ServerExtensionType.MOD,
                                Set.of(ServerPlatform.FABRIC),
                                Set.of("1.21.1"),
                                null,
                                null,
                                null,
                                0L
                        )
                ),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
        ExtensionCatalogService service = new ExtensionCatalogService(new ExtensionCatalogRegistry(List.of(provider)));

        List<ExtensionCatalogEntry> results = service.search(new ExtensionCatalogQuery(
                "",
                ServerPlatform.FABRIC,
                ServerExtensionType.MOD,
                "1.21.1",
                20
        ));

        assertThat(results).extracting(ExtensionCatalogEntry::projectId).containsExactly("mod");
    }

    @Test
    void shouldSortAggregatedSearchByDownloadCountWhenRequested() throws IOException {
        ExtensionCatalogProvider provider = new FakeProvider(
                "mixed",
                "Mixed",
                ExtensionSourceType.MODRINTH,
                List.of(
                        new ExtensionCatalogEntry(
                                "mixed",
                                "low",
                                "l1",
                                "Low Downloads",
                                "Example",
                                "1.0.0",
                                "Mod result",
                                ExtensionSourceType.MODRINTH,
                                ServerExtensionType.MOD,
                                Set.of(ServerPlatform.FABRIC),
                                Set.of("1.21.1"),
                                null,
                                null,
                                null,
                                5L
                        ),
                        new ExtensionCatalogEntry(
                                "mixed",
                                "high",
                                "h1",
                                "High Downloads",
                                "Example",
                                "1.0.0",
                                "Mod result",
                                ExtensionSourceType.MODRINTH,
                                ServerExtensionType.MOD,
                                Set.of(ServerPlatform.FABRIC),
                                Set.of("1.21.1"),
                                null,
                                null,
                                null,
                                500L
                        )
                ),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
        ExtensionCatalogService service = new ExtensionCatalogService(new ExtensionCatalogRegistry(List.of(provider)));

        List<ExtensionCatalogEntry> results = service.search(new ExtensionCatalogQuery(
                "",
                ServerPlatform.FABRIC,
                ServerExtensionType.MOD,
                "1.21.1",
                20,
                "downloads"
        ));

        assertThat(results).extracting(ExtensionCatalogEntry::projectId).containsExactly("high", "low");
    }

    @Test
    void shouldDeduplicateSearchResultsByProviderAndProjectIdentity() throws IOException {
        ExtensionCatalogProvider provider = new FakeProvider(
                "modrinth",
                "Modrinth",
                ExtensionSourceType.MODRINTH,
                List.of(
                        new ExtensionCatalogEntry(
                                "modrinth",
                                "P1",
                                "old",
                                "ViaVersion",
                                "ViaVersion",
                                "1.0.0",
                                "Protocol plugin",
                                ExtensionSourceType.MODRINTH,
                                ServerExtensionType.PLUGIN,
                                Set.of(ServerPlatform.PAPER),
                                Set.of("1.21.1"),
                                null,
                                "https://modrinth.com/plugin/viaversion",
                                null,
                                10L
                        ),
                        new ExtensionCatalogEntry(
                                "modrinth",
                                "P1",
                                "new",
                                "ViaVersion",
                                "ViaVersion",
                                "2.0.0",
                                "Protocol plugin duplicate",
                                ExtensionSourceType.MODRINTH,
                                ServerExtensionType.PLUGIN,
                                Set.of(ServerPlatform.PAPER),
                                Set.of("1.21.1"),
                                null,
                                "https://modrinth.com/plugin/viaversion",
                                null,
                                100L
                        ),
                        new ExtensionCatalogEntry(
                                "modrinth",
                                "P2",
                                "other",
                                "ViaBackwards",
                                "ViaVersion",
                                "1.0.0",
                                "Backwards protocol plugin",
                                ExtensionSourceType.MODRINTH,
                                ServerExtensionType.PLUGIN,
                                Set.of(ServerPlatform.PAPER),
                                Set.of("1.21.1"),
                                null,
                                "https://modrinth.com/plugin/viabackwards",
                                null,
                                50L
                        )
                ),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
        ExtensionCatalogService service = new ExtensionCatalogService(new ExtensionCatalogRegistry(List.of(provider)));

        List<ExtensionCatalogEntry> results = service.search(new ExtensionCatalogQuery(
                "via",
                ServerPlatform.PAPER,
                ServerExtensionType.PLUGIN,
                "1.21.1",
                20,
                "downloads"
        ));

        assertThat(results).extracting(ExtensionCatalogEntry::projectId).containsExactly("P1", "P2");
        assertThat(results.getFirst().versionId()).isEqualTo("new");
    }

    @Test
    void shouldKeepIncompatibleTypedSearchResultsWhenCompatibilityFiltersAreNotRequested() throws IOException {
        ExtensionCatalogProvider provider = new FakeProvider(
                "mixed",
                "Mixed",
                ExtensionSourceType.MODRINTH,
                List.of(
                        new ExtensionCatalogEntry(
                                "mixed",
                                "fabric",
                                "f1",
                                "Fabric Mod",
                                "Example",
                                "1.0.0",
                                "Mod result",
                                ExtensionSourceType.MODRINTH,
                                ServerExtensionType.MOD,
                                Set.of(ServerPlatform.FABRIC),
                                Set.of("1.21.1"),
                                null,
                                null,
                                null,
                                50L
                        ),
                        new ExtensionCatalogEntry(
                                "mixed",
                                "forge",
                                "g1",
                                "Forge Mod",
                                "Example",
                                "1.0.0",
                                "Mod result",
                                ExtensionSourceType.MODRINTH,
                                ServerExtensionType.MOD,
                                Set.of(ServerPlatform.FORGE),
                                Set.of("1.20.1"),
                                null,
                                null,
                                null,
                                25L
                        )
                ),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
        ExtensionCatalogService service = new ExtensionCatalogService(new ExtensionCatalogRegistry(List.of(provider)));

        List<ExtensionCatalogEntry> results = service.search(new ExtensionCatalogQuery(
                "mod",
                ServerPlatform.UNKNOWN,
                ServerExtensionType.MOD,
                "",
                20,
                "downloads"
        ));

        assertThat(results).extracting(ExtensionCatalogEntry::projectId).containsExactly("fabric", "forge");
    }


    @Test
    void shouldRejectDownloadPlanWhenProviderReturnsWrongEcosystem() throws IOException {
        ExtensionDownloadPlan wrongPlan = new ExtensionDownloadPlan(
                "mixed",
                "plugin",
                "p1",
                "1.0.0",
                null,
                "Plugin.jar",
                "https://example.test/Plugin.jar",
                ExtensionSourceType.MODRINTH,
                ServerExtensionType.PLUGIN,
                ServerPlatform.PAPER,
                "1.21.1",
                true,
                "Ready"
        );
        ExtensionCatalogProvider provider = new FakeProvider(
                "mixed",
                "Mixed",
                ExtensionSourceType.MODRINTH,
                List.of(),
                Optional.empty(),
                Optional.of(wrongPlan),
                List.of()
        );
        ExtensionCatalogService service = new ExtensionCatalogService(new ExtensionCatalogRegistry(List.of(provider)));
        Server server = new Server();
        server.setPlatform(ServerPlatform.FABRIC);
        server.setVersion("1.21.1");

        assertThat(service.resolveDownload("mixed", "plugin", "p1", server)).isEmpty();
    }

    @Test
    void shouldResolveDetailsDownloadsAndUpdatesFromProviderContract() throws IOException {
        ExtensionCatalogDetails details = new ExtensionCatalogDetails(
                new ExtensionCatalogEntry(
                        "hangar",
                        "31",
                        "24490",
                        "ViaVersion",
                        "ViaVersion",
                        "5.9.0-SNAPSHOT+976",
                        "Compatibilidad",
                        ExtensionSourceType.HANGAR,
                        ServerExtensionType.PLUGIN,
                        Set.of(ServerPlatform.PAPER),
                        Set.of("1.21.1"),
                        null,
                        "https://hangar.papermc.io/ViaVersion/ViaVersion",
                        "https://hangarcdn.papermc.io/example.jar",
                        0L
                ),
                "Long description",
                "https://hangar.papermc.io/ViaVersion/ViaVersion",
                "https://github.com/ViaVersion/ViaVersion/issues",
                "GPL",
                Set.of("misc"),
                List.of(new ExtensionCatalogVersion(
                        "hangar",
                        "31",
                        "24490",
                        "5.9.0-SNAPSHOT+976",
                        "5.9.0-SNAPSHOT+976",
                        Set.of(ServerPlatform.PAPER),
                        Set.of("1.21.1"),
                        "Fixes",
                        "ViaVersion.jar",
                        "https://hangarcdn.papermc.io/example.jar",
                        1L
                ))
        );
        ExtensionDownloadPlan downloadPlan = new ExtensionDownloadPlan(
                "hangar",
                "31",
                "24490",
                "5.9.0-SNAPSHOT+976",
                "https://cdn.example/vv.webp",
                "ViaVersion.jar",
                "https://hangarcdn.papermc.io/example.jar",
                ExtensionSourceType.HANGAR,
                ServerExtensionType.PLUGIN,
                ServerPlatform.PAPER,
                "1.21.1",
                true,
                "OK"
        );
        ExtensionCatalogVersion updateVersion = new ExtensionCatalogVersion(
                "hangar",
                "31",
                "24490",
                "5.9.0-SNAPSHOT+976",
                "5.9.0-SNAPSHOT+976",
                Set.of(ServerPlatform.PAPER),
                Set.of("1.21.1"),
                "Fixes",
                "ViaVersion.jar",
                "https://hangarcdn.papermc.io/example.jar",
                1L
        );
        ExtensionCatalogProvider provider = new FakeProvider(
                "hangar",
                "Hangar",
                ExtensionSourceType.HANGAR,
                List.of(),
                Optional.of(details),
                Optional.of(downloadPlan),
                List.of()
        ) {
            @Override
            public List<ExtensionUpdateCandidate> findUpdates(Server server,
                                                              List<ServerExtension> installedExtensions) {
                return List.of(new ExtensionUpdateCandidate(
                        "hangar",
                        "31",
                        installedExtensions.getFirst(),
                        updateVersion,
                        true,
                        "Hay update"
                ));
            }
        };
        ExtensionCatalogService service = new ExtensionCatalogService(
                new ExtensionCatalogRegistry(List.of(provider))
        );
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("1.21.1");

        ServerExtension installed = new ServerExtension();
        installed.setDisplayName("ViaVersion");
        installed.setVersion("5.2.1");
        ExtensionSource source = new ExtensionSource();
        source.setType(ExtensionSourceType.HANGAR);
        source.setProvider("hangar");
        source.setProjectId("31");
        installed.setSource(source);
        server.setExtensions(List.of(installed));

        ExtensionCatalogDetails resolvedDetails = service.getDetails(
                "hangar",
                "31",
                new ExtensionCatalogQuery("viaversion", ServerPlatform.PAPER, ServerExtensionType.PLUGIN, "1.21.1", 10)
        ).orElseThrow();
        ExtensionDownloadPlan resolvedPlan = service.resolveDownload("hangar", "31", null, server).orElseThrow();
        List<ExtensionUpdateCandidate> updates = service.findUpdates(server);

        assertThat(resolvedDetails.entry().displayName()).isEqualTo("ViaVersion");
        assertThat(resolvedPlan.ready()).isTrue();
        assertThat(resolvedPlan.providerId()).isEqualTo("hangar");
        assertThat(resolvedPlan.sourceType()).isEqualTo(ExtensionSourceType.HANGAR);
        assertThat(updates).singleElement().satisfies(update -> {
            assertThat(update.updateAvailable()).isTrue();
            assertThat(update.targetVersion().versionNumber()).isEqualTo("5.9.0-SNAPSHOT+976");
        });
    }

    @Test
    void shouldContinueSearchWhenOneProviderFails() throws IOException {
        ExtensionCatalogProvider failingProvider = new FakeProvider(
                "broken",
                "Broken",
                ExtensionSourceType.MODRINTH,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of()
        ) {
            @Override
            public List<ExtensionCatalogEntry> search(ExtensionCatalogQuery query) throws IOException {
                throw new IOException("boom");
            }
        };
        ExtensionCatalogProvider workingProvider = new FakeProvider(
                "hangar",
                "Hangar",
                ExtensionSourceType.HANGAR,
                List.of(new ExtensionCatalogEntry(
                        "hangar",
                        "31",
                        null,
                        "ViaVersion",
                        "ViaVersion",
                        null,
                        "Compatibilidad",
                        ExtensionSourceType.HANGAR,
                        ServerExtensionType.PLUGIN,
                        Set.of(ServerPlatform.PAPER),
                        Set.of("1.21.1"),
                        null,
                        null,
                        null,
                        0L
                )),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
        ExtensionCatalogService service = new ExtensionCatalogService(new ExtensionCatalogRegistry(List.of(failingProvider, workingProvider)));

        List<ExtensionCatalogEntry> results = service.search(new ExtensionCatalogQuery(
                "via",
                ServerPlatform.PAPER,
                ServerExtensionType.PLUGIN,
                "1.21.1",
                10
        ));

        assertThat(results).singleElement().extracting(ExtensionCatalogEntry::providerId).isEqualTo("hangar");
    }

    @Test
    void shouldFailSearchWhenAllProvidersFail() {
        ExtensionCatalogProvider failingProvider = new FakeProvider(
                "broken",
                "Broken",
                ExtensionSourceType.MODRINTH,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of()
        ) {
            @Override
            public List<ExtensionCatalogEntry> search(ExtensionCatalogQuery query) throws IOException {
                throw new IOException("boom");
            }
        };
        ExtensionCatalogService service = new ExtensionCatalogService(new ExtensionCatalogRegistry(List.of(failingProvider)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.search(new ExtensionCatalogQuery(
                        "via",
                        ServerPlatform.PAPER,
                        ServerExtensionType.PLUGIN,
                        "1.21.1",
                        10
                )))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("ningun proveedor");
    }

    private static class FakeProvider implements ExtensionCatalogProvider {
        private final String providerId;
        private final String displayName;
        private final ExtensionSourceType sourceType;
        private final List<ExtensionCatalogEntry> searchResults;
        private final Optional<ExtensionCatalogDetails> details;
        private final Optional<ExtensionDownloadPlan> downloadPlan;
        private final List<ExtensionUpdateCandidate> updates;

        private FakeProvider(String providerId,
                             String displayName,
                             ExtensionSourceType sourceType,
                             List<ExtensionCatalogEntry> searchResults,
                             Optional<ExtensionCatalogDetails> details,
                             Optional<ExtensionDownloadPlan> downloadPlan,
                             List<ExtensionUpdateCandidate> updates) {
            this.providerId = providerId;
            this.displayName = displayName;
            this.sourceType = sourceType;
            this.searchResults = searchResults;
            this.details = details;
            this.downloadPlan = downloadPlan;
            this.updates = updates;
        }

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public ExtensionSourceType getSourceType() {
            return sourceType;
        }

        @Override
        public Set<ExtensionCatalogCapability> getCapabilities() {
            return Set.of(
                    ExtensionCatalogCapability.SEARCH,
                    ExtensionCatalogCapability.DETAILS,
                    ExtensionCatalogCapability.DOWNLOAD,
                    ExtensionCatalogCapability.UPDATES
            );
        }

        @Override
        public List<ExtensionCatalogEntry> search(ExtensionCatalogQuery query) throws IOException {
            return searchResults;
        }

        @Override
        public Optional<ExtensionCatalogDetails> getDetails(String projectId,
                                                            ExtensionCatalogQuery query) throws IOException {
            return details;
        }

        @Override
        public Optional<ExtensionDownloadPlan> resolveDownload(String projectId,
                                                               String versionId,
                                                               Server server) throws IOException {
            return downloadPlan;
        }

        @Override
        public List<ExtensionUpdateCandidate> findUpdates(Server server,
                                                          List<ServerExtension> installedExtensions) throws IOException {
            return updates;
        }
    }
}
