package controlador.extensions;

import modelo.Server;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModrinthExtensionCatalogProviderTest {
    @Test
    void shouldFilterSearchByMinecraftVersionAndLoader() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/search?query=sodium&limit=10&index=relevance&facets=%5B%5B%22project_type%3Amod%22%5D%2C%5B%22categories%3Afabric%22%5D%2C%5B%22versions%3A1.21.1%22%5D%5D",
                """
                {
                  "hits": [
                    {
                      "project_id": "A1",
                      "slug": "sodium",
                      "project_type": "mod",
                      "title": "Sodium",
                      "author": "jellysquid",
                      "description": "Performance mod",
                      "categories": ["fabric"],
                      "versions": ["1.21.1"],
                      "icon_url": "https://cdn.example/sodium.png",
                      "latest_version": "v100"
                    },
                    {
                      "project_id": "A2",
                      "slug": "other",
                      "project_type": "mod",
                      "title": "Other",
                      "author": "someone",
                      "description": "Wrong version",
                      "categories": ["fabric"],
                      "versions": ["1.20.6"],
                      "latest_version": "v200"
                    }
                  ]
                }
                """
        )));

        List<ExtensionCatalogEntry> results = provider.search(
                new ExtensionCatalogQuery("sodium", ServerPlatform.FABRIC, ServerExtensionType.MOD, "1.21.1", 10)
        );

        assertThat(results).singleElement().satisfies(entry -> {
            assertThat(entry.projectId()).isEqualTo("A1");
            assertThat(entry.sourceType()).isEqualTo(ExtensionSourceType.MODRINTH);
            assertThat(entry.compatiblePlatforms()).contains(ServerPlatform.FABRIC);
            assertThat(entry.compatibleMinecraftVersions()).contains("1.21.1");
        });
    }

    @Test
    void shouldResolveCompatibleDownloadForSelectedServer() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/project/sodium",
                """
                {
                  "id": "A1",
                  "slug": "sodium",
                  "project_type": "mod",
                  "title": "Sodium",
                  "description": "Performance mod",
                  "body": "Long description",
                  "categories": ["fabric"],
                  "game_versions": ["1.21.1"],
                  "license": { "name": "MIT" }
                }
                """,
                "https://api.modrinth.com/v2/project/sodium/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.1%22%5D&featured=true&include_changelog=false",
                """
                [
                  {
                    "id": "v100",
                    "name": "Sodium 0.6.0",
                    "version_number": "0.6.0",
                    "loaders": ["fabric"],
                    "game_versions": ["1.21.1"],
                    "date_published": "2026-03-01T12:00:00Z",
                    "files": [
                      {
                        "primary": true,
                        "filename": "sodium-fabric-0.6.0.jar",
                        "url": "https://cdn.modrinth.com/data/A1/versions/v100/sodium-fabric-0.6.0.jar"
                      }
                    ]
                  }
                ]
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.FABRIC);
        server.setVersion("1.21.1");

        ExtensionDownloadPlan plan = provider.resolveDownload("sodium", null, server).orElseThrow();

        assertThat(plan.sourceType()).isEqualTo(ExtensionSourceType.MODRINTH);
        assertThat(plan.extensionType()).isEqualTo(ServerExtensionType.MOD);
        assertThat(plan.platform()).isEqualTo(ServerPlatform.FABRIC);
        assertThat(plan.minecraftVersionConstraint()).contains("1.21.1");
        assertThat(plan.downloadUrl()).contains("sodium-fabric-0.6.0.jar");
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
