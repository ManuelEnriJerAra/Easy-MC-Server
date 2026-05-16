package modelo.automation;

import java.time.Duration;

public enum AutomationIntervalUnit {
    SECONDS,
    MINUTES,
    HOURS;

    public Duration toDuration(int amount) {
        return switch (this) {
            case SECONDS -> Duration.ofSeconds(amount);
            case MINUTES -> Duration.ofMinutes(amount);
            case HOURS -> Duration.ofHours(amount);
        };
    }
}
