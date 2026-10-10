package net.elrid.hardmode.zone;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

public final class SafeZoneNotifications {

    private SafeZoneNotifications() {}

    public static void broadcast(ServerLevel level, Component msg) {
        for (Player player : level.players()) {
            player.sendSystemMessage(msg);
        }
    }

    public static void warning(ServerLevel level, SafeZonePhase phase, long remainingTicks) {
        int s = (int) Math.ceil(remainingTicks / 20.0);
        Component msg = switch (phase) {
            case BEGIN -> line(
                    "⚠ Начальная зона заканчивает сжатие через " + s + " сек!",
                    ChatFormatting.YELLOW);
            case MARCH -> line(
                    "⚠ Зона скоро остановится через " + s + " сек.",
                    ChatFormatting.YELLOW);
            case REST_SHORT -> line(
                    "⚠ Зона начнёт расширяться через " + s + " сек.",
                    ChatFormatting.YELLOW);
            case EXPAND -> line(
                    "⚠ Зона скоро уйдёт на новый маршрут через " + s + " сек.",
                    ChatFormatting.YELLOW);
            case REST_LONG -> line(
                    "⚠ Зона снова начнёт движение через " + s + " сек. Приготовьтесь!",
                    ChatFormatting.YELLOW);
            case EYE_CHASE -> line(
                    "👁 Следование за Оком завершается через " + s + " сек.",
                    ChatFormatting.LIGHT_PURPLE);
            case EYE_HOLD_SUCCESS -> line(
                    "🏛 Зона покинет крепость через " + s + " сек.",
                    ChatFormatting.AQUA);
            case EYE_HOLD_FAIL -> line(
                    "Зона вернётся к движению через " + s + " сек.",
                    ChatFormatting.GRAY);
        };
        broadcast(level, msg);
    }

    public static void phaseChanged(ServerLevel level, SafeZonePhase phase) {
        Component msg = switch (phase) {
            case BEGIN -> line(
                    "⚑ НАЧАЛО ИГРЫ. Радиус " + (int) SafeZoneConfig.RADIUS_BEGIN
                            + " блоков. Зона будет сжиматься.",
                    ChatFormatting.GREEN);
            case MARCH -> line(
                    "☠ Зона НАЧАЛА ДВИЖЕНИЕ на " + (int) SafeZoneConfig.travelDistance()
                            + " блоков и сужается. Не отставайте!",
                    ChatFormatting.RED);
            case REST_SHORT -> line(
                    "⏸ Зона ОСТАНОВИЛАСЬ. Короткая передышка.",
                    ChatFormatting.GRAY);
            case EXPAND -> line(
                    "✔ Зона РАСШИРЯЕТСЯ до " + (int) SafeZoneConfig.radiusWork() + " блоков.",
                    ChatFormatting.GREEN);
            case REST_LONG -> line(
                    "⏸ Зона отдыхает " + (SafeZoneConfig.REST_LONG_DURATION / 1200)
                            + " минут. Используйте время с умом.",
                    ChatFormatting.GRAY);
            case EYE_CHASE -> line(
                    "👁 Зона СЛЕДУЕТ за Оком Эндера. Приготовьтесь к остановке.",
                    ChatFormatting.LIGHT_PURPLE);
            case EYE_HOLD_SUCCESS -> line(
                    "🏛 Зона НАШЛА КРЕПОСТЬ! Остаётся здесь 5 минут.",
                    ChatFormatting.AQUA);
            case EYE_HOLD_FAIL -> line(
                    "✗ Крепость не найдена. Зона остановится на 30 секунд.",
                    ChatFormatting.GRAY);
        };
        broadcast(level, msg);
    }

    public static void eyeModeActivated(ServerLevel level) {
        broadcast(level, line("👁 Око Эндера активировано! Зона следует за ним.",
                ChatFormatting.LIGHT_PURPLE));
    }

    private static Component line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }
}