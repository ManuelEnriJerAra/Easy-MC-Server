package controlador.extensions;

import modelo.Server;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HangarExtensionCatalogProviderTest {
    @Test
    void shouldFilterSearchByPaperPlatformAndMinecraftVersion() throws IOException {
        HangarExtensionCatalogProvider provider = new HangarExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://hangar.papermc.io/api/v1/projects?query=viaversion&platform=PAPER&version=1.21.1&limit=10&offset=0&sort=-downloads",
                """
                {
                  "result": [
                    {
                      "id": 31,
                      "name": "ViaVersion",
                      "description": "Compatibility plugin",
                      "avatarUrl": "https://cdn.example/vv.webp",
                      "category": "misc",
                      "namespace": { "owner": "ViaVersion", "slug": "ViaVersion" },
                      "supportedPlatforms": {
                        "PAPER": ["1.20.6", "1.21.1"]
                      }
                    },
                    {
                      "id": 32,
                      "name": "WrongVersion",
                      "description": "Old plugin",
                      "namespace": { "owner": "Legacy", "slug": "WrongVersion" },
                      "supportedPlatforms": {
                        "PAPER": ["1.20.6"]
                      }
                    }
                  ]
                }
                """
        )));

        List<ExtensionCatalogEntry> results = provider.search(new ExtensionCatalogQuery(
                "viaversion",
                ServerPlatform.PAPER,
                ServerExtensionType.PLUGIN,
                "1.21.1",
                10
        ));

        assertThat(results).singleElement().satisfies(entry -> {
            assertThat(entry.projectId()).isEqualTo("31");
            assertThat(entry.sourceType()).isEqualTo(ExtensionSourceType.HANGAR);
            assertThat(entry.extensionType()).isEqualTo(ServerExtensionType.PLUGIN);
            assertThat(entry.compatiblePlatforms()).contains(ServerPlatform.PAPER);
            assertThat(entry.compatibleMinecraftVersions()).contains("1.21.1");
        });
    }

    @Test
    void shouldResolveCompatibleDownloadForSelectedPaperServer() throws IOException {
        HangarExtensionCatalogProvider provider = new HangarExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://hangar.papermc.io/api/v1/projects/31",
                """
                {
                  "id": 31,
                  "name": "ViaVersion",
                  "description": "Compatibility plugin",
                  "mainPageContent": "Long description",
                  "category": "misc",
                  "avatarUrl": "https://cdn.example/vv.webp",
                  "namespace": { "owner": "ViaVersion", "slug": "ViaVersion" },
                  "settings": {
                    "license": { "name": "GPL" },
                    "keywords": ["compatibility"],
                    "tags": ["SUPPORTS_FOLIA"],
                    "links": [
                      {
                        "links": [
                          { "name": "Issues", "url": "https://github.com/ViaVersion/ViaVersion/issues" }
                        ]
                      }
                    ]
                  },
                  "supportedPlatforms": {
                    "PAPER": ["1.21.1"]
                  }
                }
                """,
                "https://hangar.papermc.io/api/v1/pages/main/31",
                "Detailed page from Hangar",
                "https://hangar.papermc.io/api/v1/projects/ViaVersion/ViaVersion/versions?platform=PAPER&platformVersion=1.21.1&limit=20&offset=0",
                """
                {
                  "result": [
                    {
                      "id": 24490,
                      "name": "5.9.0-SNAPSHOT+976",
                      "description": "Fixes",
                      "createdAt": "2026-04-17T20:35:04.983531Z",
                      "downloads": {
                        "PAPER": {
                          "fileInfo": { "name": "ViaVersion-5.9.0-SNAPSHOT.jar" },
                          "downloadUrl": "https://hangarcdn.papermc.io/plugins/ViaVersion/ViaVersion/versions/5.9.0-SNAPSHOT%2B976/PAPER/ViaVersion-5.9.0-SNAPSHOT.jar"
                        }
                      },
                      "platformDependencies": {
                        "PAPER": ["1.21.1", "1.21.4"]
                      },
                      "dependencies": [
                        { "projectId": "LuckPerms", "displayName": "LuckPerms", "type": "required", "required": true },
                        { "projectId": "Vault", "displayName": "Vault", "type": "optional", "required": false }
                      ]
                    }
                  ]
                }
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("1.21.1");

        ExtensionCatalogDetails details = provider.getDetails(
                "31",
                new ExtensionCatalogQuery("viaversion", ServerPlatform.PAPER, ServerExtensionType.PLUGIN, "1.21.1", 20)
        ).orElseThrow();
        ExtensionDownloadPlan plan = provider.resolveDownload("31", null, server).orElseThrow();

        assertThat(details.entry().displayName()).isEqualTo("ViaVersion");
        assertThat(details.summary()).isEqualTo("Detailed page from Hangar");
        assertThat(details.websiteUrl()).isEqualTo("https://hangar.papermc.io/ViaVersion/ViaVersion");
        assertThat(details.issuesUrl()).isEqualTo("https://github.com/ViaVersion/ViaVersion/issues");
        assertThat(plan.sourceType()).isEqualTo(ExtensionSourceType.HANGAR);
        assertThat(plan.extensionType()).isEqualTo(ServerExtensionType.PLUGIN);
        assertThat(plan.platform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(plan.minecraftVersionConstraint()).contains("1.21.1");
        assertThat(plan.downloadUrl()).contains("ViaVersion-5.9.0-SNAPSHOT.jar");
        assertThat(plan.description()).isEqualTo("Detailed page from Hangar");
        assertThat(plan.dependencies()).anySatisfy(dependency -> {
            assertThat(dependency.projectId()).isEqualTo("LuckPerms");
            assertThat(dependency.required()).isTrue();
        });
        assertThat(plan.dependencies()).anySatisfy(dependency -> {
            assertThat(dependency.projectId()).isEqualTo("Vault");
            assertThat(dependency.required()).isFalse();
        });
    }

    @Test
    void shouldPagePastHangarTwentyFiveResultApiLimit() throws IOException {
        HangarExtensionCatalogProvider provider = new HangarExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://hangar.papermc.io/api/v1/projects?platform=PAPER&version=1.21.1&limit=25&offset=0&sort=-downloads",
                projectsPage(1, 25),
                "https://hangar.papermc.io/api/v1/projects?platform=PAPER&version=1.21.1&limit=25&offset=25&sort=-downloads",
                projectsPage(26, 25),
                "https://hangar.papermc.io/api/v1/projects?platform=PAPER&version=1.21.1&limit=10&offset=50&sort=-downloads",
                projectsPage(51, 10)
        )));

        List<ExtensionCatalogEntry> results = provider.search(new ExtensionCatalogQuery(
                "",
                ServerPlatform.PAPER,
                ServerExtensionType.PLUGIN,
                "1.21.1",
                60
        ));

        assertThat(results).hasSize(60);
        assertThat(results.getFirst().projectId()).isEqualTo("1");
        assertThat(results.getLast().projectId()).isEqualTo("60");
    }

    @Test
    void shouldReadHangarDownloadCountFromNestedStats() throws IOException {
        HangarExtensionCatalogProvider provider = new HangarExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://hangar.papermc.io/api/v1/projects?query=popular&platform=PAPER&version=1.21.1&limit=10&offset=0&sort=-downloads",
                """
                {
                  "result": [
                    {
                      "id": 31,
                      "name": "Popular",
                      "description": "Popular plugin",
                      "namespace": { "owner": "Example", "slug": "Popular" },
                      "supportedPlatforms": { "PAPER": ["1.21.1"] },
                      "stats": { "downloads": 12345 }
                    }
                  ]
                }
                """
        )));

        List<ExtensionCatalogEntry> results = provider.search(new ExtensionCatalogQuery(
                "popular",
                ServerPlatform.PAPER,
                ServerExtensionType.PLUGIN,
                "1.21.1",
                10
        ));

        assertThat(results).singleElement().extracting(ExtensionCatalogEntry::downloads).isEqualTo(12345L);
    }

    @Test
    void shouldResolveModrinthExternalDownloadWithoutUsingHtmlProjectPage() throws IOException {
        HangarExtensionCatalogProvider provider = new HangarExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://hangar.papermc.io/api/v1/projects/99",
                """
                {
                  "id": 99,
                  "name": "External Plugin",
                  "namespace": { "owner": "ExampleOwner", "slug": "ExternalPlugin" },
                  "supportedPlatforms": { "PAPER": ["1.21.1"] }
                }
                """,
                "https://hangar.papermc.io/api/v1/projects/ExampleOwner/ExternalPlugin/versions?platform=PAPER&platformVersion=1.21.1&limit=20&offset=0",
                """
                {
                  "result": [
                    {
                      "id": 12345,
                      "name": "1.2",
                      "createdAt": "2026-04-26T14:54:00.066905Z",
                      "downloads": {
                        "PAPER": {
                          "fileInfo": null,
                          "externalUrl": "https://modrinth.com/plugin/external-plugin",
                          "downloadUrl": null
                        }
                      },
                      "platformDependencies": { "PAPER": ["1.21.1"] }
                    }
                  ]
                }
                """,
                "https://api.modrinth.com/v2/project/external-plugin/version?loaders=%5B%22paper%22%5D&game_versions=%5B%221.21.1%22%5D&include_changelog=false",
                """
                [
                  {
                    "id": "version-one",
                    "name": "External Plugin 1.2",
                    "version_number": "1.2",
                    "files": [
                      {
                        "primary": true,
                        "filename": "ExternalPlugin-1.2.jar",
                        "url": "https://cdn.modrinth.com/data/example/versions/version-one/ExternalPlugin-1.2.jar"
                      }
                    ]
                  }
                ]
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("1.21.1");

        ExtensionDownloadPlan plan = provider.resolveDownload("99", null, server).orElseThrow();

        assertThat(plan.fileName()).isEqualTo("ExternalPlugin-1.2.jar");
        assertThat(plan.downloadUrl()).isEqualTo("https://cdn.modrinth.com/data/example/versions/version-one/ExternalPlugin-1.2.jar");
        assertThat(plan.downloadUrl()).doesNotContain("modrinth.com/plugin");
    }

    @Test
    void shouldResolveDirectExternalJarDownloadForNovaPaper2612() throws IOException {
        HangarExtensionCatalogProvider provider = new HangarExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://hangar.papermc.io/api/v1/projects/626",
                """
                {
                  "id": 626,
                  "name": "Nova",
                  "description": "A server-side modding framework for Paper",
                  "namespace": { "owner": "xenondevs", "slug": "Nova" },
                  "supportedPlatforms": { "PAPER": ["26.1.2"] }
                }
                """,
                "https://hangar.papermc.io/api/v1/projects/xenondevs/Nova/versions?platform=PAPER&platformVersion=26.1.2&limit=20&offset=0",
                """
                {
                  "result": [
                    {
                      "id": 25168,
                      "name": "0.23.0",
                      "createdAt": "2026-05-10T09:37:52.620899Z",
                      "downloads": {
                        "PAPER": {
                          "fileInfo": null,
                          "externalUrl": "https://github.com/xenondevs/Nova/releases/download/0.23.0/Nova-0.23.0%2BMC-26.1.2.jar",
                          "downloadUrl": null
                        }
                      },
                      "platformDependencies": { "PAPER": ["26.1.2"] }
                    }
                  ]
                }
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("26.1.2");

        ExtensionDownloadPlan plan = provider.resolveDownload("626", null, server).orElseThrow();

        assertThat(plan.versionId()).isEqualTo("25168");
        assertThat(plan.versionNumber()).isEqualTo("0.23.0");
        assertThat(plan.fileName()).isEqualTo("Nova-0.23.0+MC-26.1.2.jar");
        assertThat(plan.downloadUrl()).isEqualTo("https://github.com/xenondevs/Nova/releases/download/0.23.0/Nova-0.23.0%2BMC-26.1.2.jar");
        assertThat(plan.minecraftVersionConstraint()).isEqualTo("26.1.2");
    }

    @Test
    void shouldNotResolveExternalWebsiteAsDownloadWhenNoJarCanBeFound() throws IOException {
        HangarExtensionCatalogProvider provider = new HangarExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://hangar.papermc.io/api/v1/projects/99",
                """
                {
                  "id": 99,
                  "name": "External Plugin",
                  "namespace": { "owner": "ExampleOwner", "slug": "ExternalPlugin" },
                  "supportedPlatforms": { "PAPER": ["1.21.1"] }
                }
                """,
                "https://hangar.papermc.io/api/v1/projects/ExampleOwner/ExternalPlugin/versions?platform=PAPER&platformVersion=1.21.1&limit=20&offset=0",
                """
                {
                  "result": [
                    {
                      "id": 12345,
                      "name": "1.2",
                      "createdAt": "2026-04-26T14:54:00.066905Z",
                      "downloads": {
                        "PAPER": {
                          "fileInfo": null,
                          "externalUrl": "https://example.com/external-plugin",
                          "downloadUrl": null
                        }
                      },
                      "platformDependencies": { "PAPER": ["1.21.1"] }
                    }
                  ]
                }
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("1.21.1");

        assertThat(provider.resolveDownload("99", null, server)).isEmpty();
    }

    @Test
    void shouldFindUpdatesForInstalledHangarPlugins() throws IOException {
        HangarExtensionCatalogProvider provider = new HangarExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://hangar.papermc.io/api/v1/projects/31",
                """
                {
                  "id": 31,
                  "name": "ViaVersion",
                  "namespace": { "owner": "ViaVersion", "slug": "ViaVersion" },
                  "supportedPlatforms": { "PAPER": ["1.21.1"] }
                }
                """,
                "https://hangar.papermc.io/api/v1/projects/ViaVersion/ViaVersion/versions?platform=PAPER&platformVersion=1.21.1&limit=20&offset=0",
                """
                {
                  "result": [
                    {
                      "id": 24490,
                      "name": "5.9.0-SNAPSHOT+976",
                      "description": "Fixes",
                      "createdAt": "2026-04-17T20:35:04.983531Z",
                      "downloads": {
                        "PAPER": {
                          "fileInfo": { "name": "ViaVersion-5.9.0-SNAPSHOT.jar" },
                          "downloadUrl": "https://hangarcdn.papermc.io/plugins/ViaVersion/ViaVersion/versions/5.9.0-SNAPSHOT%2B976/PAPER/ViaVersion-5.9.0-SNAPSHOT.jar"
                        }
                      },
                      "platformDependencies": {
                        "PAPER": ["1.21.1"]
                      }
                    }
                  ]
                }
                """
        )));
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

        List<ExtensionUpdateCandidate> updates = provider.findUpdates(server, List.of(installed));

        assertThat(updates).singleElement().satisfies(update -> {
            assertThat(update.providerId()).isEqualTo("hangar");
            assertThat(update.updateAvailable()).isTrue();
            assertThat(update.targetVersion().versionNumber()).isEqualTo("5.9.0-SNAPSHOT+976");
        });
    }

    @Test
    void shouldRespectSelectedVersionIdWhenResolvingDownloadPlan() throws IOException {
        HangarExtensionCatalogProvider provider = new HangarExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://hangar.papermc.io/api/v1/projects/31",
                """
                {
                  "id": 31,
                  "name": "ViaVersion",
                  "namespace": { "owner": "ViaVersion", "slug": "ViaVersion" },
                  "supportedPlatforms": { "PAPER": ["1.21.1"] }
                }
                """,
                "https://hangar.papermc.io/api/v1/pages/main/31",
                "Detailed page from Hangar",
                "https://hangar.papermc.io/api/v1/projects/ViaVersion/ViaVersion/versions?platform=PAPER&platformVersion=1.21.1&limit=20&offset=0",
                """
                {
                  "result": [
                    {
                      "id": 24490,
                      "name": "5.9.0",
                      "createdAt": "2026-04-17T20:35:04.983531Z",
                      "downloads": {
                        "PAPER": {
                          "fileInfo": { "name": "ViaVersion-latest.jar" },
                          "downloadUrl": "https://hangarcdn.example/ViaVersion-latest.jar"
                        }
                      },
                      "platformDependencies": { "PAPER": ["1.21.1"] }
                    },
                    {
                      "id": 20000,
                      "name": "5.8.0",
                      "createdAt": "2026-03-17T20:35:04.983531Z",
                      "downloads": {
                        "PAPER": {
                          "fileInfo": { "name": "ViaVersion-selected.jar" },
                          "downloadUrl": "https://hangarcdn.example/ViaVersion-selected.jar"
                        }
                      },
                      "platformDependencies": { "PAPER": ["1.21.1"] }
                    }
                  ]
                }
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("1.21.1");

        ExtensionDownloadPlan selected = provider.resolveDownload("31", "20000", server).orElseThrow();

        assertThat(selected.versionId()).isEqualTo("20000");
        assertThat(selected.versionNumber()).isEqualTo("5.8.0");
        assertThat(selected.fileName()).isEqualTo("ViaVersion-selected.jar");
        assertThat(provider.resolveDownload("31", "missing", server)).isEmpty();
    }

    @Test
    void shouldPreferReleaseOverNewerSnapshotByDefault() throws IOException {
        HangarExtensionCatalogProvider provider = new HangarExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://hangar.papermc.io/api/v1/projects/31",
                """
                {
                  "id": 31,
                  "name": "ViaVersion",
                  "namespace": { "owner": "ViaVersion", "slug": "ViaVersion" },
                  "supportedPlatforms": { "PAPER": ["1.21.1"] }
                }
                """,
                "https://hangar.papermc.io/api/v1/pages/main/31",
                "Detailed page from Hangar",
                "https://hangar.papermc.io/api/v1/projects/ViaVersion/ViaVersion/versions?platform=PAPER&platformVersion=1.21.1&limit=20&offset=0",
                """
                {
                  "result": [
                    {
                      "id": 24490,
                      "name": "5.9.0-SNAPSHOT+976",
                      "createdAt": "2026-04-17T20:35:04.983531Z",
                      "downloads": {
                        "PAPER": {
                          "fileInfo": { "name": "ViaVersion-snapshot.jar" },
                          "downloadUrl": "https://hangarcdn.example/ViaVersion-snapshot.jar"
                        }
                      },
                      "platformDependencies": { "PAPER": ["1.21.1"] }
                    },
                    {
                      "id": 20000,
                      "name": "5.8.0",
                      "createdAt": "2026-03-17T20:35:04.983531Z",
                      "downloads": {
                        "PAPER": {
                          "fileInfo": { "name": "ViaVersion-release.jar" },
                          "downloadUrl": "https://hangarcdn.example/ViaVersion-release.jar"
                        }
                      },
                      "platformDependencies": { "PAPER": ["1.21.1"] }
                    }
                  ]
                }
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("1.21.1");

        ExtensionDownloadPlan selected = provider.resolveDownload("31", null, server).orElseThrow();

        assertThat(selected.versionId()).isEqualTo("20000");
        assertThat(selected.versionNumber()).isEqualTo("5.8.0");
        assertThat(selected.fileName()).isEqualTo("ViaVersion-release.jar");
    }

    @Test
    void shouldAdvertisePaperPluginSupportOnly() {
        ExtensionCatalogProviderDescriptor descriptor = new HangarExtensionCatalogProvider().describeProvider();

        assertThat(descriptor.supportedExtensionTypes()).containsExactly(ServerExtensionType.PLUGIN);
        assertThat(descriptor.supportedPlatforms()).contains(ServerPlatform.PAPER, ServerPlatform.PURPUR, ServerPlatform.PUFFERFISH);
        assertThat(new HangarExtensionCatalogProvider().supportsQuery(new ExtensionCatalogQuery(
                "sodium",
                ServerPlatform.FABRIC,
                ServerExtensionType.MOD,
                "1.21.1",
                10
        ))).isFalse();
    }

    private static final class FakeHttpClient extends ExtensionHttpClient {
        private final Map<String, String> responses;

        private FakeHttpClient(Map<String, String> responses) {
            this.responses = responses;
        }

        @Override
        String get(URI uri, Map<String, String> headers) throws IOException {
            String body = responses.get(uri.toString());
            if (body == null) {
                throw new IOException("URI no stubbeada en test: " + uri);
            }
            return body;
        }
    }

    private static String projectsPage(int firstId, int count) {
        StringBuilder result = new StringBuilder("{\"result\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                result.append(',');
            }
            int id = firstId + i;
            result.append("""
                    {
                      "id": %d,
                      "name": "Plugin %d",
                      "namespace": { "owner": "Owner%d", "slug": "Plugin%d" },
                      "supportedPlatforms": { "PAPER": ["1.21.1"] },
                      "stats": { "downloads": %d }
                    }
                    """.formatted(id, id, id, id, 1000 - id));
        }
        result.append("]}");
        return result.toString();
    }
}
