package vista;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PanelMundoDebugConnectionsTest {
    @Test
    void debugRecentConnectionsArePrependedAndCapped() {
        List<PanelMundo.RecentConnection> debugConnections = List.of(
                connection("DebugPlayer004"),
                connection("DebugPlayer003"),
                connection("DebugPlayer002"),
                connection("DebugPlayer001")
        );
        List<PanelMundo.RecentConnection> realConnections = List.of(
                connection("Alex"),
                connection("Steve")
        );

        List<PanelMundo.RecentConnection> result = PanelMundo.mergeDebugRecentConnections(
                debugConnections,
                realConnections,
                4
        );

        assertThat(result)
                .extracting(PanelMundo.RecentConnection::username)
                .containsExactly("DebugPlayer004", "DebugPlayer003", "DebugPlayer002", "DebugPlayer001");
    }

    @Test
    void debugRecentConnectionsFillBeforeRealConnections() {
        List<PanelMundo.RecentConnection> result = PanelMundo.mergeDebugRecentConnections(
                List.of(connection("DebugPlayer001")),
                List.of(connection("Alex"), connection("Steve")),
                4
        );

        assertThat(result)
                .extracting(PanelMundo.RecentConnection::username)
                .containsExactly("DebugPlayer001", "Alex", "Steve");
    }

    @Test
    void debugRecentConnectionsReturnEmptyWhenCapIsZero() {
        List<PanelMundo.RecentConnection> result = PanelMundo.mergeDebugRecentConnections(
                List.of(connection("DebugPlayer001")),
                List.of(connection("Alex")),
                0
        );

        assertThat(result).isEmpty();
    }

    private PanelMundo.RecentConnection connection(String username) {
        return new PanelMundo.RecentConnection(username, "Hoy 12:00", "X: 0 Z: 0", 0L);
    }
}
