package net.elrid.hardmode.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.elrid.hardmode.zone.SafeZoneState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class ZonePositionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("zone_position")
                        .executes(ZonePositionCommand::execute)
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        // Убедимся, что команда выполняется в оверворлде
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            source.sendFailure(Component.literal("Команда доступна только в Оверворлде.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Получаем данные о зоне
        SafeZoneState.Phase phase = SafeZoneState.getPhase();
        Vec3 center = SafeZoneState.getCenter(level);
        double radius = SafeZoneState.getRadius(level);

        // Вычисляем оставшееся время фазы
        long now = level.getGameTime();
        long phaseStart = SafeZoneState.debugGetPhaseStartTick();
        long duration = (phase == SafeZoneState.Phase.STATIC)
                ? SafeZoneState.STATIC_DURATION
                : SafeZoneState.MOVING_DURATION;
        long elapsed = now - phaseStart;
        long remaining = Math.max(0, duration - elapsed);

        // Форматируем вывод
        String phaseName = (phase == SafeZoneState.Phase.STATIC)
                ? "СТОИТ" : "ДВИЖЕТСЯ";
        ChatFormatting phaseColor = (phase == SafeZoneState.Phase.STATIC)
                ? ChatFormatting.GREEN : ChatFormatting.RED;

        source.sendSuccess(() -> Component.literal("═══ Состояние зоны ═══")
                .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal("Фаза: ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(phaseName).withStyle(phaseColor)), false);

        source.sendSuccess(() -> Component.literal(String.format(
                        "Центр: X=%.1f, Z=%.1f", center.x, center.z))
                .withStyle(ChatFormatting.AQUA), false);

        source.sendSuccess(() -> Component.literal(String.format(
                        "Радиус: %.1f блоков", radius))
                .withStyle(ChatFormatting.AQUA), false);

        source.sendSuccess(() -> Component.literal(String.format(
                        "Осталось: %d сек (%d тиков)",
                        remaining / 20, remaining))
                .withStyle(ChatFormatting.YELLOW), false);

        return 1;
    }
}
