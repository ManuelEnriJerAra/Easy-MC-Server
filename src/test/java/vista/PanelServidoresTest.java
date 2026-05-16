package vista;

import controlador.GestorServidores;
import modelo.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.lang.reflect.Constructor;
import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PanelServidoresTest {
    @TempDir
    Path tempDir;

    @Test
    void muestraServidoresAunqueNoTenganVersionDetectada() {
        GestorServidores gestor = gestorServidores(tempDir.resolve("servers.json").toFile());
        Server server = new Server();
        server.setDisplayName("Imported Paper");
        server.setServerDir(tempDir.resolve("paper").toString());
        server.setVersion(null);
        gestor.guardarServidor(server);

        PanelServidores panel = new PanelServidores(gestor);
        Component view = panel.getViewport().getView();

        assertThat(view).isInstanceOf(JPanel.class);
        assertThat(((JPanel) view).getComponentCount()).isEqualTo(1);
    }

    @Test
    void refrescarListado_debeMantenerPosicionScrollActual() throws Exception {
        GestorServidores gestor = gestorServidores(tempDir.resolve("servers.json").toFile());
        for (int i = 0; i < 14; i++) {
            Server server = new Server();
            server.setDisplayName(String.format("Server %02d", i));
            server.setServerDir(tempDir.resolve("server-" + i).toString());
            server.setVersion("1.21.1");
            gestor.guardarServidor(server);
        }

        PanelServidores[] holder = new PanelServidores[1];
        SwingUtilities.invokeAndWait(() -> {
            PanelServidores panel = new PanelServidores(gestor);
            panel.setSize(new Dimension(260, 180));
            panel.doLayout();
            Component view = panel.getViewport().getView();
            view.setSize(view.getPreferredSize());
            panel.getViewport().setViewPosition(new Point(0, 360));
            holder[0] = panel;
        });

        Point before = holder[0].getViewport().getViewPosition();
        Server server = gestor.getListaServidores().get(8);
        server.setDisplayName("Server 08 updated");
        gestor.guardarServidor(server);
        flushEdt();
        flushEdt();

        Point after = holder[0].getViewport().getViewPosition();
        assertThat(before.y).isGreaterThan(0);
        assertThat(after.y).isEqualTo(before.y);
    }

    private void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private GestorServidores gestorServidores(File jsonFile) {
        try {
            Constructor<GestorServidores> constructor = GestorServidores.class.getDeclaredConstructor(File.class);
            constructor.setAccessible(true);
            return constructor.newInstance(jsonFile);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
