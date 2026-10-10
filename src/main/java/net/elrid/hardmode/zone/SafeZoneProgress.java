package net.elrid.hardmode.zone;

public final class SafeZoneProgress {
    private static int completedCycles = 0;

    private SafeZoneProgress() {}

    public static int getCompletedCycles() { return completedCycles; }

    /** 0.0 — начало игры, 1.0 — максимум. */
    public static float getProgress() {
        if (SafeZoneConfig.MAX_PROGRESS_CYCLES <= 0) return 1.0F;
        return Math.min(1.0F, completedCycles / (float) SafeZoneConfig.MAX_PROGRESS_CYCLES);
    }

    public static void incrementCycle() { completedCycles++; }

    public static void reset() { completedCycles = 0; }

    public static void set(int cycles) { completedCycles = Math.max(0, cycles); }
}
