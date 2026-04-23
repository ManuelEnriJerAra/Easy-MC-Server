package controlador;

import modelo.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GestorServidoresTest {
    @TempDir
    Path tempDir;

    @Test
    void guardarServidor_debeCompletarMetadatosYPersistirEnJsonAislado() throws Exception {
        Path jsonPath = tempDir.resolve("easy-mc-server-list.json");
        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        Server server = new Server();
        server.setId(null);
        server.setDisplayName("Alpha");
        server.setServerConfig(null);

        gestor.guardarServidor(server);

        List<Server> persisted = new ObjectMapper().readValue(jsonPath.toFile(),
                new tools.jackson.core.type.TypeReference<>() {});
        assertThat(persisted).hasSize(1);
        assertThat(persisted.getFirst().getId()).isNotBlank();
        assertThat(persisted.getFirst().getServerConfig()).isNotNull();
        assertThat(persisted.getFirst().getOrdenLista()).isZero();
        assertThat(persisted.getFirst().getPreviewRenderProfileId()).isEqualTo("quality");
        assertThat(persisted.getFirst().getPreviewRenderRealtime()).isFalse();
        assertThat(persisted.getFirst().getPreviewShowSpawn()).isFalse();
        assertThat(persisted.getFirst().getPreviewShowPlayers()).isFalse();
        assertThat(persisted.getFirst().getPreviewShowChunkGrid()).isFalse();
        assertThat(persisted.getFirst().getPreviewUseWholeMap()).isFalse();
        assertThat(persisted.getFirst().getPreviewRenderLimitPixels()).isEqualTo(256);
        assertThat(persisted.getFirst().getPreviewRenderCenterId()).isEqualTo("spawn");
        assertThat(persisted.getFirst().getEstadisticasCpuActiva()).isTrue();
        assertThat(persisted.getFirst().getEstadisticasCpuHistorial()).isTrue();
    }

    @Test
    void constructor_debeCompletarPreferenciasPreviewFaltantes() throws Exception {
        Path jsonPath = tempDir.resolve("easy-mc-server-list.json");
        Path serverDir = tempDir.resolve("previewless-server");
        TestWorldFixtures.createValidServerJar(serverDir, "server.jar");
        Server server = new Server();
        server.setDisplayName("Previewless");
        server.setServerDir(serverDir.toString());
        server.setPreviewRenderProfileId(null);
        server.setPreviewRenderRealtime(null);
        server.setPreviewShowSpawn(null);
        server.setPreviewShowPlayers(null);
        server.setPreviewShowChunkGrid(null);
        server.setPreviewUseWholeMap(null);
        server.setPreviewRenderLimitPixels(null);
        server.setPreviewRenderCenterId(null);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), List.of(server));

        GestorServidores gestor = new GestorServidores(jsonPath.toFile());

        Server persisted = gestor.getListaServidores().getFirst();
        assertThat(persisted.getPreviewRenderProfileId()).isEqualTo("quality");
        assertThat(persisted.getPreviewRenderRealtime()).isFalse();
        assertThat(persisted.getPreviewShowSpawn()).isFalse();
        assertThat(persisted.getPreviewShowPlayers()).isFalse();
        assertThat(persisted.getPreviewShowChunkGrid()).isFalse();
        assertThat(persisted.getPreviewUseWholeMap()).isFalse();
        assertThat(persisted.getPreviewRenderLimitPixels()).isEqualTo(256);
        assertThat(persisted.getPreviewRenderCenterId()).isEqualTo("spawn");
        assertThat(persisted.getEstadisticasCpuActiva()).isTrue();
        assertThat(persisted.getEstadisticasCpuHistorial()).isTrue();
    }

    @Test
    void establecerFavoritoYReordenarServidores_debenMantenerOrdenVisualEstable() {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("easy-mc-server-list.json").toFile());
        Server alpha = new Server();
        alpha.setDisplayName("Alpha");
        Server beta = new Server();
        beta.setDisplayName("Beta");
        Server gamma = new Server();
        gamma.setDisplayName("Gamma");
        gestor.guardarServidor(alpha);
        gestor.guardarServidor(beta);
        gestor.guardarServidor(gamma);

        gestor.establecerFavorito(beta, true);
        gestor.reordenarServidores(List.of(gamma.getId(), alpha.getId(), beta.getId()));

        List<Server> ordered = gestor.getListaServidores();
        assertThat(ordered.getFirst().getId()).isEqualTo(beta.getId());
        assertThat(ordered).extracting(Server::getId).containsExactly(beta.getId(), gamma.getId(), alpha.getId());
    }

    @Test
    void constructor_debeEliminarServidoresPersistidosNoCargables() throws Exception {
        Path jsonPath = tempDir.resolve("easy-mc-server-list.json");
        Path validDir = tempDir.resolve("valid-server");
        Path invalidDir = tempDir.resolve("invalid-server");
        Files.createDirectories(invalidDir);
        TestWorldFixtures.createValidServerJar(validDir, "server.jar");

        Server valid = new Server();
        valid.setDisplayName("Valid");
        valid.setServerDir(validDir.toString());

        Server invalid = new Server();
        invalid.setDisplayName("Invalid");
        invalid.setServerDir(invalidDir.toString());

        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), List.of(valid, invalid));

        GestorServidores gestor = new GestorServidores(jsonPath.toFile());

        assertThat(gestor.getListaServidores()).extracting(Server::getDisplayName).containsExactly("Valid");
        assertThat(gestor.getAvisoServidoresNoCargados()).contains("No se han podido cargar 1 servidores");
    }

    @Test
    void eliminarServidor_debeFallarSiProcesoSigueVivo() {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("easy-mc-server-list.json").toFile());
        Server server = new Server();
        server.setDisplayName("Busy");
        gestor.guardarServidor(server);
        server.setServerProcess(new FakeProcess(true));

        boolean deleted = gestor.eliminarServidor(server);

        assertThat(deleted).isFalse();
        assertThat(gestor.getListaServidores()).extracting(Server::getId).contains(server.getId());
    }

    private static final class FakeProcess extends Process {
        private final boolean alive;

        private FakeProcess(boolean alive) {
            this.alive = alive;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) {
            return !alive;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }

        @Override
        public Process destroyForcibly() {
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
