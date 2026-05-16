package vista;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelIndicadoresRecursosTest {
    @Test
    void resourceCardsStayInOneRowWithReadableMetricLabels() throws Exception {
        runOnEdtAndWait(() -> {
            PanelIndicadoresRecursos panel = new PanelIndicadoresRecursos(null);
            panel.setSize(PanelIndicadoresRecursos.READABLE_SINGLE_ROW_WIDTH, 80);
            panel.doLayout();
            layoutRecursively(panel);

            assertEquals(3, panel.getComponentCount());
            List<Integer> cardRows = new ArrayList<>();
            for (Component component : panel.getComponents()) {
                assertTrue(component.getWidth() >= component.getMinimumSize().width);
                cardRows.add(component.getY());

                List<JLabel> labels = labelsInside(component);
                assertEquals(2, labels.size());
                for (JLabel label : labels) {
                    assertFalse(label.getText().contains("..."));
                    assertTrue(label.getWidth() >= label.getPreferredSize().width,
                            () -> label.getText() + " should keep enough width to paint without ellipsis");
                }
            }

            assertEquals(1, cardRows.stream().distinct().count(),
                    "The metric cards should stay in one stable row");
        });
    }

    private static void runOnEdtAndWait(Runnable runnable) throws InvocationTargetException, InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        SwingUtilities.invokeAndWait(runnable);
    }

    private static void layoutRecursively(Component component) {
        component.doLayout();
        if (!(component instanceof Container container)) {
            return;
        }
        for (Component child : container.getComponents()) {
            layoutRecursively(child);
        }
    }

    private static List<JLabel> labelsInside(Component component) {
        List<JLabel> labels = new ArrayList<>();
        collectLabels(component, labels);
        return labels;
    }

    private static void collectLabels(Component component, List<JLabel> labels) {
        if (component instanceof JLabel label) {
            labels.add(label);
        }
        if (!(component instanceof Container container)) {
            return;
        }
        for (Component child : container.getComponents()) {
            collectLabels(child, labels);
        }
    }
}
