package vista;

public record ServerResourceSnapshot(boolean running,
                                     double cpuPercent,
                                     long ramUsedMb,
                                     long ramMaxMb,
                                     double diskPercent) {

    public int cpuPercentRounded() {
        return (int) Math.round(Math.max(0d, Math.min(100d, cpuPercent)));
    }

    public int ramPercentRounded() {
        if (ramMaxMb <= 0L) {
            return 0;
        }
        double percent = (ramUsedMb * 100d) / ramMaxMb;
        return (int) Math.round(Math.max(0d, Math.min(100d, percent)));
    }

    public int diskPercentRounded() {
        return (int) Math.round(Math.max(0d, Math.min(100d, diskPercent)));
    }
}
