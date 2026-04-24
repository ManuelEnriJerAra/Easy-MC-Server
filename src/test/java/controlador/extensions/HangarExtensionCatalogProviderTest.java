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
                "https://hangar.papermc.io/api/v1/projects?query=viaversion&platform=PAPER&version=1.21.1&limit=10&offset=0&sort=stars",
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
                      }
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
        assertThat(details.websiteUrl()).isEqualTo("https://hangar.papermc.io/ViaVersion/ViaVersion");
        assertThat(details.issuesUrl()).isEqualTo("https://github.com/ViaVersion/ViaVersion/issues");
        assertThat(plan.sourceType()).isEqualTo(ExtensionSourceType.HANGAR);
        assertThat(plan.extensionType()).isEqualTo(ServerExtensionType.PLUGIN);
        assertThat(plan.platform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(plan.minecraftVersionConstraint()).contains("1.21.1");
        assertThat(plan.downloadUrl()).contains("ViaVersion-5.9.0-SNAPSHOT.jar");
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
}
