package vista;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.List;

final class DebugModeUiBinder {
    private DebugModeUiBinder() {
    }

    static PropertyChangeListener createUiListener(Runnable onDebugModeChanged) {
        return evt -> {
            if (!DebugMode.PROPERTY_ENABLED.equals(evt.getPropertyName())) return;
            SwingUtilities.invokeLater(onDebugModeChanged);
        };
    }

    static void rebuildHeaderActions(
            JPanel headerActionsPanel,
            List<? extends Component> debugActions,
            List<? extends Component> alwaysVisibleActions
    ) {
        if (headerActionsPanel == null) return;

        headerActionsPanel.removeAll();
        if (DebugMode.isEnabled() && debugActions != null) {
            for (Component action : debugActions) {
                if (action != null) {
                    headerActionsPanel.add(action);
                }
            }
        }
        if (alwaysVisibleActions != null) {
            for (Component action : alwaysVisibleActions) {
                if (action != null) {
                    headerActionsPanel.add(action);
                }
            }
        }
        headerActionsPanel.revalidate();
        headerActionsPanel.repaint();
    }
}
