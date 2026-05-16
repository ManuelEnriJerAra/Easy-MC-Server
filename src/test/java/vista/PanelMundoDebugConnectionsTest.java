package vista;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class PanelMundoDebugConnectionsTest {
    @Test
    void debugRecentConnectionsArePrependedWithoutHardCap() {
        List<WorldRecentConnectionsPanel.RecentConnection> debugConnections = List.of(
                connection("DebugPlayer005"),
                connection("DebugPlayer004"),
                connection("DebugPlayer003"),
                connection("DebugPlayer002"),
                connection("DebugPlayer001")
        );
        List<WorldRecentConnectionsPanel.RecentConnection> realConnections = List.of(
                connection("Alex"),
                connection("Steve")
        );

        List<WorldRecentConnectionsPanel.RecentConnection> result =
                WorldRecentConnectionsPanel.mergeDebugRecentConnections(debugConnections, realConnections);

        assertThat(result)
                .extracting(WorldRecentConnectionsPanel.RecentConnection::username)
                .containsExactly("DebugPlayer005", "DebugPlayer004", "DebugPlayer003", "DebugPlayer002", "DebugPlayer001", "Alex", "Steve");
    }

    @Test
    void debugRecentConnectionsFillBeforeRealConnections() {
        List<WorldRecentConnectionsPanel.RecentConnection> result =
                WorldRecentConnectionsPanel.mergeDebugRecentConnections(
                        List.of(connection("DebugPlayer001")),
                        List.of(connection("Alex"), connection("Steve"))
                );

        assertThat(result)
                .extracting(WorldRecentConnectionsPanel.RecentConnection::username)
                .containsExactly("DebugPlayer001", "Alex", "Steve");
    }

    @Test
    void debugRecentConnectionsReturnEmptyWhenThereAreNoSources() {
        List<WorldRecentConnectionsPanel.RecentConnection> result =
                WorldRecentConnectionsPanel.mergeDebugRecentConnections(List.of(), List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void debugRecentConnectionsRemoveRepeatedUsersAcrossDebugAndRealLists() {
        List<WorldRecentConnectionsPanel.RecentConnection> result =
                WorldRecentConnectionsPanel.mergeDebugRecentConnections(
                        List.of(connection("Alex"), connection("Steve")),
                        List.of(connection("Alex"), connection("Herobrine"))
                );

        assertThat(result)
                .extracting(WorldRecentConnectionsPanel.RecentConnection::username)
                .containsExactly("Alex", "Steve", "Herobrine");
    }

    @Test
    void realRecentConnectionsAreDeduplicatedWithoutHardCap() {
        List<WorldRecentConnectionsPanel.RecentConnection> result = WorldRecentConnectionsPanel.extractRecentConnectionsFromLogLines(
                List.of(
                        "[10:00:00] Alex joined the game",
                        "[10:01:00] Steve joined the game",
                        "[10:02:00] Alex joined the game",
                        "[10:03:00] Herobrine joined the game",
                        "[10:04:00] Notch joined the game",
                        "[10:05:00] Jeb joined the game",
                        "[10:06:00] Dinnerbone joined the game"
                ),
                LocalDate.of(2026, 5, 16)
        );

        assertThat(result)
                .extracting(WorldRecentConnectionsPanel.RecentConnection::username)
                .containsExactly("Dinnerbone", "Jeb", "Notch", "Herobrine", "Alex", "Steve");
    }

    private WorldRecentConnectionsPanel.RecentConnection connection(String username) {
        return new WorldRecentConnectionsPanel.RecentConnection(username, "Hoy 12:00", "X: 0 Z: 0", 0L);
    }
}
