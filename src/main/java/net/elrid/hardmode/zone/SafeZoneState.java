package net.elrid.hardmode.zone;

import net.elrid.hardmode.HardMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class SafeZoneState {

    // ==================== СОСТОЯНИЕ ====================

    private static SafeZonePhase phase          = SafeZonePhase.BEGIN;
    private static long          phaseStartTick = Long.MIN_VALUE;
    private static Vec3          phaseStartPos  = null;
    private static Vec3          phaseTargetPos = null;

    private static boolean warningSent = false;

    private static boolean eyeWillSucceed = false;
    private static double  phaseStartRadius = 0.0;

    private static SafeZonePhase lastLoggedPhase = null;

    /** Точка, откуда зона пришла в текущую стартовую (для MARCH). */
    private static @Nullable Vec3 previousPos = null;

    private SafeZoneState() {}

    // ==================== ПУБЛИЧНОЕ API ====================

    public static SafeZonePhase getPhase() { return phase; }
    public static boolean isEyeModeActive() { return phase.isEyeMode(); }

    public static long debugGetPhaseStartTick() { return phaseStartTick; }
    public static Vec3 debugGetPhaseTargetPos() { return phaseTargetPos; }
    public static boolean debugIsEyeWillSucceed() { return eyeWillSucceed; }
    public static double debugGetPhaseStartRadius() { return phaseStartRadius; }

    public static long phaseDuration(SafeZonePhase p) { return SafeZoneConfig.duration(p); }


    /** Полный сброс статики — вызывается при смене мира. */
    public static void reset() {
        phase            = SafeZonePhase.BEGIN;
        phaseStartTick   = Long.MIN_VALUE;
        phaseStartPos    = null;
        phaseTargetPos   = null;
        phaseStartRadius = 0.0;
        eyeWillSucceed   = false;
        warningSent      = false;
        lastLoggedPhase  = null;
        SafeZoneProgress.reset();
        previousPos = null;
    }

    // ==================== РЕЖИМ ОКА ====================

    public static boolean canEnterEyeMode(ServerLevel level) {
        return switch (phase) {
            case EYE_HOLD_SUCCESS -> false;
            case EYE_CHASE -> (level.getGameTime() - phaseStartTick)
                    >= SafeZoneConfig.EYE_CHASE_COOLDOWN;
            default -> true;
        };
    }

    public static String getEyeModeBlockReason(ServerLevel level) {
        return switch (phase) {
            case EYE_HOLD_SUCCESS -> "Зона отдыхает в крепости. Око сейчас не сработает.";
            case EYE_CHASE -> {
                long left = SafeZoneConfig.EYE_CHASE_COOLDOWN
                        - (level.getGameTime() - phaseStartTick);
                long sec = Math.max(1, (left + 19) / 20);
                yield "Око уже активно. Повторно — через " + sec + " сек.";
            }
            default -> null;
        };
    }

    public static boolean enterEyeMode(ServerLevel level, Vec3 direction, Vec3 strongholdPos) {
        if (!canEnterEyeMode(level)) return false;

        long now = level.getGameTime();
        double currentRadius = getRadius(level);

        Vec3 start = (phaseStartPos == null) ? defaultSpawnCenter(level) : getCenter(level);
        Vec3 end = start.add(
                direction.x * SafeZoneConfig.EYE_TRAVEL_DISTANCE, 0,
                direction.z * SafeZoneConfig.EYE_TRAVEL_DISTANCE);

        phaseStartPos     = start;
        phaseTargetPos    = end;
        phaseStartRadius  = currentRadius;
        phaseStartTick    = now;

        double dx = strongholdPos.x - end.x;
        double dz = strongholdPos.z - end.z;
        eyeWillSucceed = (dx * dx + dz * dz) <= (SafeZoneConfig.EYE_RADIUS * SafeZoneConfig.EYE_RADIUS);

        phase = SafeZonePhase.EYE_CHASE;
        warningSent = false;
        lastLoggedPhase = null;

        SafeZoneNotifications.eyeModeActivated(level);

        HardMode.LOGGER.info("[SafeZone] EYE_MODE: start=({}, {}) end=({}, {}) willSucceed={}",
                fmt(start.x), fmt(start.z), fmt(end.x), fmt(end.z), eyeWillSucceed);

        return true;
    }

    // ==================== TICK ====================

    public static void tick(MinecraftServer server, ServerLevel level) {
        long now = level.getGameTime();

        if (phaseStartTick == Long.MIN_VALUE || phaseStartPos == null) {
            enterBegin(now, defaultSpawnCenter(level));
            logState(level, "init");
            return;
        }

        long elapsed   = now - phaseStartTick;
        long duration  = SafeZoneConfig.duration(phase);
        long remaining = duration - elapsed;

        long warnThreshold = Math.min(SafeZoneConfig.WARNING_TICKS, duration / 2);
        if (!warningSent && remaining > 0 && remaining <= warnThreshold) {
            SafeZoneNotifications.warning(level, phase, remaining);
            warningSent = true;
        }

        if (elapsed >= duration) {
            transition(level, now, elapsed - duration);
            SafeZoneNotifications.phaseChanged(level, phase);
            warningSent = false;
        }

        if (phase != lastLoggedPhase) {
            logPhase(level);
            lastLoggedPhase = phase;
        }
    }

    private static void transition(ServerLevel level, long now, long overshoot) {
        SafeZonePhase next = nextPhase(phase);
        double radiusAtEnd = getRadius(level);

        applyTransition(level, next);

        // Прогресс цикла растёт при возврате из REST_LONG в MARCH.
        if (phase == SafeZonePhase.REST_LONG && next == SafeZonePhase.MARCH) {
            SafeZoneProgress.incrementCycle();
        }

        phase = next;
        phaseStartTick   = now - Math.min(overshoot, SafeZoneConfig.duration(phase) - 1);
        phaseStartRadius = radiusAtEnd;
    }

    private static SafeZonePhase nextPhase(SafeZonePhase current) {
        if (current == SafeZonePhase.EYE_CHASE) {
            return eyeWillSucceed ? SafeZonePhase.EYE_HOLD_SUCCESS : SafeZonePhase.EYE_HOLD_FAIL;
        }
        return switch (current) {
            case BEGIN      -> SafeZonePhase.MARCH;
            case MARCH      -> SafeZonePhase.REST_SHORT;
            case REST_SHORT -> SafeZonePhase.EXPAND;
            case EXPAND     -> SafeZonePhase.REST_LONG;
            case REST_LONG  -> SafeZonePhase.MARCH;
            case EYE_HOLD_FAIL, EYE_HOLD_SUCCESS -> SafeZonePhase.MARCH;
            case EYE_CHASE -> SafeZonePhase.EYE_HOLD_FAIL; // fallback
        };
    }

    private static void applyTransition(ServerLevel level, SafeZonePhase next) {
        switch (next) {
            case MARCH -> {
                Vec3 from = phaseStartPos;
                Vec3 target = chooseFarTarget(level, from, previousPos);

                previousPos = from;
                phaseStartPos  = from;
                phaseTargetPos = target;
            }
            case REST_SHORT, EXPAND, REST_LONG, EYE_HOLD_SUCCESS, EYE_HOLD_FAIL -> {
                // Остановка: центр = куда прибыли.
                phaseStartPos  = phaseTargetPos;
            }
            case BEGIN, EYE_CHASE -> {
                // Инициализируются через enterBegin / enterEyeMode.
            }
        }
    }

    private static void enterBegin(long now, Vec3 center) {
        phase            = SafeZonePhase.BEGIN;
        phaseStartTick   = now;
        phaseStartPos    = center;
        phaseTargetPos   = center;
        phaseStartRadius = SafeZoneConfig.RADIUS_BEGIN;
        previousPos = null;
    }

    // ==================== ГЕОМЕТРИЯ ====================

    public static Vec3 getCenter(ServerLevel level) {
        if (phaseStartPos == null) return defaultSpawnCenter(level);
        if (!phase.isMoving()) return phaseStartPos;

        long elapsed = level.getGameTime() - phaseStartTick;
        double t = clamp01((double) elapsed / SafeZoneConfig.duration(phase));
        double s = smoothstep(t);
        return phaseStartPos.lerp(phaseTargetPos, s);
    }

    public static double getRadius(ServerLevel level) {
        if (phaseStartPos == null) return SafeZoneConfig.RADIUS_BEGIN;

        long elapsed = level.getGameTime() - phaseStartTick;

        return switch (phase) {
            // BEGIN: сужение с phaseStartRadius (RADIUS_BEGIN) до рабочего радиуса.
            case BEGIN -> {
                double t = clamp01((double) elapsed / SafeZoneConfig.BEGIN_DURATION);
                yield lerp(phaseStartRadius, SafeZoneConfig.radiusWork(), smoothstep(t));
            }

            // MARCH: сужение с phaseStartRadius до минимума за первые 80% времени.
            case MARCH -> {
                double shrinkTime = SafeZoneConfig.MARCH_DURATION
                        * SafeZoneConfig.MARCH_SHRINK_PORTION;
                if (elapsed < shrinkTime) {
                    double t = clamp01((double) elapsed / shrinkTime);
                    yield lerp(phaseStartRadius, SafeZoneConfig.radiusMin(), smoothstep(t));
                }
                yield SafeZoneConfig.radiusMin();
            }

            // REST_SHORT: держим минимум.
            case REST_SHORT -> SafeZoneConfig.radiusMin();

            // EXPAND: от phaseStartRadius (минимум) до рабочего радиуса.
            case EXPAND -> {
                double t = clamp01((double) elapsed / SafeZoneConfig.EXPAND_DURATION);
                yield lerp(phaseStartRadius, SafeZoneConfig.radiusWork(), smoothstep(t));
            }

            // REST_LONG: держим рабочий радиус.
            case REST_LONG -> SafeZoneConfig.radiusWork();

            // EYE_CHASE: сжатие до EYE_RADIUS за EYE_SHRINK_DURATION.
            case EYE_CHASE -> {
                if (elapsed < SafeZoneConfig.EYE_SHRINK_DURATION) {
                    double t = clamp01((double) elapsed / SafeZoneConfig.EYE_SHRINK_DURATION);
                    yield lerp(phaseStartRadius, SafeZoneConfig.EYE_RADIUS, smoothstep(t));
                }
                yield SafeZoneConfig.EYE_RADIUS;
            }

            case EYE_HOLD_SUCCESS, EYE_HOLD_FAIL -> SafeZoneConfig.EYE_RADIUS;
        };
    }

    public static Vec3 defaultSpawnCenter(ServerLevel level) {
        BlockPos spawn = level.getLevelData().getRespawnData().pos();
        return new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
    }

    // ==================== ЛОГИ ====================

    private static void logPhase(ServerLevel level) {
        long elapsed  = level.getGameTime() - phaseStartTick;
        long duration = SafeZoneConfig.duration(phase);
        HardMode.LOGGER.info(
                "[SafeZone] Фаза {} elapsed={}/{} center=({}, {}) target=({}, {}) r={} progress={}",
                phase, elapsed, duration,
                fmt(phaseStartPos.x), fmt(phaseStartPos.z),
                fmt(phaseTargetPos.x), fmt(phaseTargetPos.z),
                fmt(getRadius(level)), fmtP(SafeZoneProgress.getProgress()));
    }

    private static void logState(ServerLevel level, String reason) {
        Vec3 c = getCenter(level);
        double r = getRadius(level);
        long elapsed = (phaseStartTick == Long.MIN_VALUE) ? 0 : level.getGameTime() - phaseStartTick;
        HardMode.LOGGER.info(
                "[SafeZone/{}] phase={} elapsed={} r={} center=({}, {}) target=({}, {}) cycles={}",
                reason, phase, elapsed, fmt(r),
                fmt(c.x), fmt(c.z),
                phaseTargetPos == null ? "-" : fmt(phaseTargetPos.x),
                phaseTargetPos == null ? "-" : fmt(phaseTargetPos.z),
                SafeZoneProgress.getCompletedCycles());
    }

    public static void debugPrint(ServerLevel level) { logState(level, "manual"); }

    // ==================== ПЕРСИСТЕНТНОСТЬ ====================

    public static void loadFrom(SafeZoneSavedData data, ServerLevel level) {
        if (!data.hasData()) {
            HardMode.LOGGER.info("[SafeZone] Сохранённых данных нет — сброс статики");
            reset();
            return;
        }

        previousPos = data.hasPrevious() ? data.getPreviousPos() : null;
        long currentSeed = level.getSeed();
        long savedSeed   = data.getWorldSeed();
        long savedTime   = data.getSavedGameTime();
        long currentTime = level.getGameTime();
        long phaseStart  = data.getPhaseStartTick();

        if ((savedSeed != 0L && savedSeed != currentSeed)
                || (savedTime > 0L && currentTime < savedTime)
                || (phaseStart > currentTime)) {
            HardMode.LOGGER.warn("[SafeZone] Мир не совпадает с сохранением — сброс зоны");
            data.reset();
            reset();
            return;
        }

        phase          = data.getPhase();
        phaseStartTick = data.getPhaseStartTick();
        phaseStartPos  = data.getPhaseStartPos();
        phaseTargetPos = data.getPhaseTargetPos();
        eyeWillSucceed   = data.isEyeWillSucceed();
        phaseStartRadius = data.getPhaseStartRadius();

        SafeZoneProgress.set(data.getCompletedCycles());

        warningSent     = false;
        lastLoggedPhase = null;

        HardMode.LOGGER.info(
                "[SafeZone] Восстановлено: phase={} startTick={} start=({}, {}) target=({}, {}) cycles={}",
                phase, phaseStartTick,
                fmt(phaseStartPos.x), fmt(phaseStartPos.z),
                fmt(phaseTargetPos.x), fmt(phaseTargetPos.z),
                SafeZoneProgress.getCompletedCycles());
    }

    public static void saveTo(SafeZoneSavedData data, ServerLevel level) {
        if (phaseStartPos == null || phaseTargetPos == null || phaseStartTick == Long.MIN_VALUE) return;
        data.set(phase, phaseStartTick, phaseStartPos, phaseTargetPos, previousPos,
                eyeWillSucceed, phaseStartRadius,
                SafeZoneProgress.getCompletedCycles(),
                level.getSeed(), level.getGameTime());
    }

    // ==================== ХЕЛПЕРЫ ====================

    private static double smoothstep(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }
    private static String fmt(double v) { return String.format("%.1f", v); }
    private static String fmtP(float v) { return String.format("%.2f", v); }

    /**
     * Генерирует 3 случайных кандидата на расстоянии travelDistance от {@code from}
     * и возвращает самый удалённый от {@code previous}.
     * Если {@code previous == null} (первый марш) — просто случайное направление.
     */
    private static Vec3 chooseFarTarget(ServerLevel level, Vec3 from, @Nullable Vec3 previous) {
        double distance = SafeZoneConfig.travelDistance();

        if (previous == null) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            return new Vec3(
                    from.x + Math.cos(angle) * distance,
                    from.y,
                    from.z + Math.sin(angle) * distance);
        }

        Vec3 best = null;
        double bestDistSq = -1.0;
        for (int i = 0; i < SafeZoneConfig.TARGET_CANDIDATES; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            Vec3 c = new Vec3(
                    from.x + Math.cos(angle) * distance,
                    from.y,
                    from.z + Math.sin(angle) * distance);
            double dx = c.x - previous.x;
            double dz = c.z - previous.z;
            double distSq = dx * dx + dz * dz;
            if (distSq > bestDistSq) {
                bestDistSq = distSq;
                best = c;
            }
        }
        return best;
    }
}