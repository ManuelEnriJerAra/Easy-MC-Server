package vista;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

final class DebugMode {
    static final String PROPERTY_ENABLED = "enabled";

    private static final PropertyChangeSupport CHANGES = new PropertyChangeSupport(DebugMode.class);
    private static boolean enabled;

    private DebugMode() {
    }

    static boolean isEnabled() {
        return enabled;
    }

    static void toggle() {
        setEnabled(!enabled);
    }

    static void setEnabled(boolean nextEnabled) {
        boolean oldEnabled = enabled;
        if (oldEnabled == nextEnabled) return;
        enabled = nextEnabled;
        CHANGES.firePropertyChange(PROPERTY_ENABLED, oldEnabled, nextEnabled);
    }

    static void addPropertyChangeListener(PropertyChangeListener listener) {
        CHANGES.addPropertyChangeListener(listener);
    }

    static void removePropertyChangeListener(PropertyChangeListener listener) {
        CHANGES.removePropertyChangeListener(listener);
    }
}
