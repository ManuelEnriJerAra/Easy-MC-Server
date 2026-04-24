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
        assertThat(providers).allSatisfy(provider ->
                assertThat(provider.capabilities()).contains(
                        ExtensionCatalogCapability.SEARCH,
                        ExtensionCatalogCapability.DETAILS,
                        ExtensionCatalogCapability.DOWNLOAD,
                        ExtensionCatalogCapability.UPDATES
                ));
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
                        null
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
                        null
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
                        "https://hangarcdn.papermc.io/example.jar"
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
                        null
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
