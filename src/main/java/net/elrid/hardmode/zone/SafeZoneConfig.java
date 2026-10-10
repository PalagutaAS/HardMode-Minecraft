package net.elrid.hardmode.zone;

public final class SafeZoneConfig {

    // ===== Длительности обычных фаз =====
    public static final int BEGIN_DURATION      = 20 * 60 * 4;
    public static final int MARCH_DURATION      = 20 * 60 * 2;
    public static final int REST_SHORT_DURATION = 20 * 20;
    public static final int EXPAND_DURATION     = 20 * 40;
    public static final int REST_LONG_DURATION  = 20 * 60 * 4;

    /** Доля MARCH, за которую заканчивается сужение (0.8 = первые 80% времени). */
    public static final double MARCH_SHRINK_PORTION = 0.8;

    // ===== Око Эндера =====
    public static final int    EYE_DURATION              = 20 * 60 * 1;
    public static final int    EYE_SHRINK_DURATION       = 20 * 20;
    public static final int    EYE_HOLD_SUCCESS_DURATION = 20 * 60 * 5;
    public static final int    EYE_HOLD_FAIL_DURATION    = 20 * 30;
    public static final double EYE_RADIUS                = 60.0;
    public static final double EYE_TRAVEL_DISTANCE       = 100.0;
    public static final int    EYE_CHASE_COOLDOWN        = 20 * 10;

    // ===== Базовые радиусы (при progress = 0) =====
    public static final double RADIUS_BEGIN     = 1000.0;
    public static final double RADIUS_WORK_BASE = 100.0;
    public static final double RADIUS_MIN_BASE  = 40.0;

    // ===== Прогрессия =====
    public static final double TRAVEL_DISTANCE_BASE = 200.0;
    public static final double TRAVEL_DISTANCE_MAX  = 1000.0;
    public static final double RADIUS_SHRINK_FACTOR = 0.5;
    public static final int    MAX_PROGRESS_CYCLES  = 20;
    public static final int    TARGET_CANDIDATES = 3;


    // ===== Уведомления =====
    public static final int WARNING_TICKS = 20 * 30;
    public static final int POISON_EFFECT_DURATION = 20 * 4;

    public static final int POISON_RING_TIER1 = 20;
    public static final int POISON_RING_TIER2 = 50;
    public static final int POISON_RING_TIER3 = 100;

    private SafeZoneConfig() {}

    // ==================== ДЛИТЕЛЬНОСТИ ====================

    public static long duration(SafeZonePhase phase) {
        return switch (phase) {
            case BEGIN      -> BEGIN_DURATION;
            case MARCH      -> MARCH_DURATION;
            case REST_SHORT -> REST_SHORT_DURATION;
            case EXPAND     -> EXPAND_DURATION;
            case REST_LONG  -> REST_LONG_DURATION;
            case EYE_CHASE        -> EYE_DURATION;
            case EYE_HOLD_SUCCESS -> EYE_HOLD_SUCCESS_DURATION;
            case EYE_HOLD_FAIL    -> EYE_HOLD_FAIL_DURATION;
        };
    }

    // ==================== ПРОГРЕССИВНЫЕ ГЕТТЕРЫ ====================

    /** Текущая дальность MARCH (200 → 1000). */
    public static double travelDistance() {
        float p = SafeZoneProgress.getProgress();
        return TRAVEL_DISTANCE_BASE + (TRAVEL_DISTANCE_MAX - TRAVEL_DISTANCE_BASE) * p;
    }

    /** Текущий рабочий радиус (100 → 50). Конец BEGIN, начало MARCH, цель EXPAND. */
    public static double radiusWork() {
        return RADIUS_WORK_BASE * shrinkFactor(SafeZoneProgress.getProgress());
    }

    /** Текущий минимальный радиус (50 → 25). Цель MARCH. */
    public static double radiusMin() {
        return RADIUS_MIN_BASE * shrinkFactor(SafeZoneProgress.getProgress());
    }

    private static double shrinkFactor(float progress) {
        return 1.0 - (1.0 - RADIUS_SHRINK_FACTOR) * progress;
    }
}