package net.elrid.hardmode.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.elrid.hardmode.zone.SafeZonePhase;
import net.elrid.hardmode.zone.SafeZoneState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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

        if (!level.dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("Команда доступна только в Оверворлде.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        SafeZonePhase phase = SafeZoneState.getPhase();
        Vec3 center = SafeZoneState.getCenter(level);
        double radius = SafeZoneState.getRadius(level);

        long now = level.getGameTime();
        long phaseStart = SafeZoneState.debugGetPhaseStartTick();
        long duration = SafeZoneState.phaseDuration(phase);
        long elapsed = now - phaseStart;
        long remaining = Math.max(0, duration - elapsed);

        String phaseName = switch (phase) {
            case BEGIN      -> "НАЧАЛО";
            case MARCH      -> "ДВИЖЕНИЕ";
            case REST_SHORT -> "КОРОТКИЙ ОТДЫХ";
            case EXPAND     -> "РАСШИРЕНИЕ";
            case REST_LONG  -> "ДОЛГИЙ ОТДЫХ";
            case EYE_CHASE        -> "СЛЕДУЕТ ЗА ОКОМ";
            case EYE_HOLD_FAIL    -> "ОСТАНОВКА (нет крепости)";
            case EYE_HOLD_SUCCESS -> "ОСТАНОВКА В КРЕПОСТИ";
        };
        ChatFormatting phaseColor = switch (phase) {
            case BEGIN      -> ChatFormatting.GREEN;
            case MARCH      -> ChatFormatting.RED;
            case REST_SHORT -> ChatFormatting.GRAY;
            case EXPAND     -> ChatFormatting.GREEN;
            case REST_LONG  -> ChatFormatting.GRAY;
            case EYE_CHASE, EYE_HOLD_SUCCESS, EYE_HOLD_FAIL -> ChatFormatting.LIGHT_PURPLE;
        };

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

        // Для фазы MOVE — дополнительно покажем, куда летит зона.
        if (phase == SafeZonePhase.MARCH) {
            Vec3 target = SafeZoneState.debugGetPhaseTargetPos();
            if (target != null) {
                double dx = target.x - center.x;
                double dz = target.z - center.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                source.sendSuccess(() -> Component.literal(String.format(
                                "Цель: X=%.1f, Z=%.1f (осталось %.1f блоков)",
                                target.x, target.z, dist))
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
        }

        return 1;
    }
}