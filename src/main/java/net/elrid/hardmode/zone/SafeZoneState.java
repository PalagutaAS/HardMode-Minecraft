package net.elrid.hardmode.zone;

import net.elrid.hardmode.HardMode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class SafeZoneState {

    public static final int STATIC_DURATION = 20 * 60 * 1;       // 1 мин
    public static final int MOVING_DURATION = 20 * 60 * 3;   // 5 мин

    public static final double BASE_RADIUS     = 25.0;
    public static final double MAX_RADIUS      = 100.0;
    public static final double TRAVEL_DISTANCE = 300.0;

    /** ★ Сколько тиков до конца фазы показывать предупреждение (в идеале 30 сек = 600). */
    private static final int WARNING_TICKS = 20 * 30;
    // ★ Предупреждение уже отправлено в текущей фазе?
    private static boolean warningSent = false;

    public static long debugGetPhaseStartTick() {
        return phaseStartTick;
    }

    public enum Phase { STATIC, MOVING }

    private static Phase phase             = Phase.STATIC;
    private static long  phaseStartTick    = Long.MIN_VALUE;
    private static Vec3  phaseStartPos     = null;
    private static Vec3  phaseTargetPos    = null;

    private static Phase lastLoggedPhase   = null;

    private SafeZoneState() {}

    // ==================== TICK ====================

    public static void tick(MinecraftServer server, ServerLevel level) {
        long now = level.getGameTime();

        // Первая инициализация — если данных нет вообще.
        if (phaseStartTick == Long.MIN_VALUE || phaseStartPos == null) {
            enterStatic(now, defaultSpawnCenter(level));
            logState(level, "init");
            return;
        }

        long elapsed   = now - phaseStartTick;
        long duration  = phaseDuration();
        long remaining = duration - elapsed;

        // ★ Предупреждение за WARNING_TICKS до конца фазы.
        // Берём минимум из WARNING_TICKS и половины фазы, чтобы на коротких фазах
        // предупреждение не срабатывало мгновенно после старта.
        long warnThreshold = Math.min(WARNING_TICKS, duration / 2);
        if (!warningSent && remaining > 0 && remaining <= warnThreshold) {
            broadcastPhaseWarning(level, remaining);
            warningSent = true;
        }

        // Переход к следующей фазе.
        if (elapsed >= duration) {
            transition(level, now, elapsed - duration);
            broadcastPhaseChanged(level);
            warningSent = false; // сбрасываем для следующей фазы
        }

        if (phase != lastLoggedPhase) {
            onPhaseChanged(level);
            lastLoggedPhase = phase;
        }
    }

    /**
     * Переход к следующей фазе.
     * overshoot — сколько тиков мы «пересидели» в предыдущей (например, при загрузке мира,
     * когда прошло больше, чем длительность фазы). Учитываем, чтобы не «съедать» время.
     */
    private static void transition(ServerLevel level, long now, long overshoot) {
        if (phase == Phase.STATIC) {
            // STATIC → MOVING
            Vec3 from = phaseStartPos;
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            Vec3 to = new Vec3(
                    from.x + Math.cos(angle) * TRAVEL_DISTANCE,
                    from.y,
                    from.z + Math.sin(angle) * TRAVEL_DISTANCE
            );
            phaseStartPos  = from;
            phaseTargetPos = to;
            phase          = Phase.MOVING;
        } else {
            // MOVING → STATIC (в конце пути)
            phaseStartPos  = phaseTargetPos;
            phase          = Phase.STATIC;
        }
        // Сдвигаем startTick так, чтобы «хвост» ушедшего времени не потерялся.
        phaseStartTick = now - Math.min(overshoot, phaseDuration() - 1);

        HardMode.LOGGER.info("[SafeZone] Переход к фазе {} @ tick={} (overshoot={})",
                phase, phaseStartTick, overshoot);
    }

    private static void enterStatic(long now, Vec3 center) {
        phase          = Phase.STATIC;
        phaseStartTick = now;
        phaseStartPos  = center;
        phaseTargetPos = center;
    }

    private static long phaseDuration() {
        return (phase == Phase.STATIC) ? STATIC_DURATION : MOVING_DURATION;
    }

    // ==================== ОПОВЕЩЕНИЯ ★ ====================

    private static void broadcastPhaseWarning(ServerLevel level, long remainingTicks) {
        int seconds = (int) Math.ceil(remainingTicks / 20.0);
        Component msg;
        if (phase == Phase.STATIC) {
            msg = Component.literal("⚠ Зона начнёт движение через " + seconds + " сек! Приготовьтесь.")
                    .withStyle(ChatFormatting.YELLOW);
        } else {
            msg = Component.literal("⚠ Зона скоро остановится через " + seconds + " сек.")
                    .withStyle(ChatFormatting.YELLOW);
        }
        broadcast(level, msg);
    }

    private static void broadcastPhaseChanged(ServerLevel level) {
        Component msg;
        if (phase == Phase.MOVING) {
            msg = Component.literal("☠ Зона НАЧАЛА ДВИЖЕНИЕ. Не отставайте!")
                    .withStyle(ChatFormatting.RED);
        } else {
            msg = Component.literal("✔ Зона ОСТАНОВИЛАСЬ. Передышка.")
                    .withStyle(ChatFormatting.GREEN);
        }
        broadcast(level, msg);
    }

    private static void broadcast(ServerLevel level, Component msg) {
        for (Player player : level.players()) {
            player.sendSystemMessage(msg);
        }
    }

    // ==================== ПОЛОЖЕНИЕ И РАДИУС ====================

    public static Vec3 getCenter(ServerLevel level) {
        if (phaseStartPos == null) return defaultSpawnCenter(level);
        if (phase == Phase.STATIC)  return phaseStartPos;

        long elapsed = level.getGameTime() - phaseStartTick;
        double t = clamp01((double) elapsed / MOVING_DURATION);
        double s = t * t * (3.0 - 2.0 * t); // smoothstep
        return phaseStartPos.lerp(phaseTargetPos, s);
    }

    public static double getRadius(ServerLevel level) {
        if (phase == Phase.STATIC) return BASE_RADIUS;
        long elapsed = level.getGameTime() - phaseStartTick;
        double t = clamp01((double) elapsed / MOVING_DURATION);
        double expansion = Math.sin(Math.PI * t);
        return BASE_RADIUS + (MAX_RADIUS - BASE_RADIUS) * expansion;
    }

    public static Phase getPhase() { return phase; }

    public static Vec3 defaultSpawnCenter(ServerLevel level) {
        BlockPos spawn = level.getLevelData().getRespawnData().pos();
        return new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
    }

    // ==================== ЛОГИ ====================

    private static void onPhaseChanged(ServerLevel level) {
        long elapsed = level.getGameTime() - phaseStartTick;
        switch (phase) {
            case STATIC -> HardMode.LOGGER.info(
                    "[SafeZone] Фаза СТОИТ. r={} центр=({}, {}), осталось {} тиков",
                    fmt(BASE_RADIUS), fmt(phaseStartPos.x), fmt(phaseStartPos.z),
                    STATIC_DURATION - elapsed);
            case MOVING -> HardMode.LOGGER.info(
                    "[SafeZone] Фаза ДВИЖЕТСЯ. from=({}, {}) to=({}, {}), осталось {} тиков",
                    fmt(phaseStartPos.x), fmt(phaseStartPos.z),
                    fmt(phaseTargetPos.x), fmt(phaseTargetPos.z),
                    MOVING_DURATION - elapsed);
        }
    }

    private static void logState(ServerLevel level, String reason) {
        Vec3 c = getCenter(level);
        double r = getRadius(level);
        long elapsed = (phaseStartTick == Long.MIN_VALUE) ? 0 : level.getGameTime() - phaseStartTick;
        HardMode.LOGGER.info(
                "[SafeZone/{}] phase={} elapsed={} r={} center=({}, {}) target=({}, {})",
                reason, phase, elapsed, fmt(r),
                fmt(c.x), fmt(c.z),
                phaseTargetPos == null ? "-" : fmt(phaseTargetPos.x),
                phaseTargetPos == null ? "-" : fmt(phaseTargetPos.z));
    }

    public static void debugPrint(ServerLevel level) {
        logState(level, "manual");
    }

    // ==================== ПЕРСИСТЕНТНОСТЬ ====================

    public static void loadFrom(SafeZoneSavedData data, ServerLevel level) {
        if (!data.hasData()) {
            HardMode.LOGGER.info("[SafeZone] Сохранённых данных нет — стартуем с нуля");
            return;
        }

        // ★ Проверка 1: сид мира изменился?
        long currentSeed = level.getSeed();
        long savedSeed = data.getWorldSeed();
        long savedTime = data.getSavedGameTime();
        long currentTime = level.getGameTime();
        long phaseStart = data.getPhaseStartTick();
        if ((savedSeed != 0L && savedSeed != currentSeed) && (savedTime > 0L && currentTime < savedTime) && (phaseStart > currentTime)) {
            data.reset();
            return;
        }

        phase          = data.getPhase();
        phaseStartTick = data.getPhaseStartTick();
        phaseStartPos  = data.getPhaseStartPos();
        phaseTargetPos = data.getPhaseTargetPos();

        HardMode.LOGGER.info(
                "[SafeZone] Восстановлено: phase={} startTick={} start=({}, {}) target=({}, {})",
                phase, phaseStartTick,
                fmt(phaseStartPos.x), fmt(phaseStartPos.z),
                fmt(phaseTargetPos.x), fmt(phaseTargetPos.z));
    }

    public static void saveTo(SafeZoneSavedData data, ServerLevel level) {
        if (phaseStartPos == null || phaseTargetPos == null || phaseStartTick == Long.MIN_VALUE) return;
        data.set(phase, phaseStartTick, phaseStartPos, phaseTargetPos, level.getSeed(), level.getGameTime());
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }
}