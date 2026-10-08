package net.elrid.hardmode.client.mixin;

import net.elrid.hardmode.zone.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class PoisonFogMixin {

    // ====== Настройки по тирам ======
    // Верхняя граница тумана (полная непрозрачность).
    private static final float TIER2_DISTANCE = 60.0F;
    private static final float TIER3_DISTANCE = 30.0F;
    private static final float TIER4_DISTANCE = 12.0F;

    /** Стартовая дистанция при появлении тумана — «далеко за горизонтом». */
    private static final float INITIAL_DISTANCE = 400.0F;

    /** Скорость сглаживания. Больше — быстрее переход. 1.0 = ~63% за сек. */
    private static final float SMOOTHING_K = 1.5F;

    /** При отуманении — если дистанция больше этого, туман считаем выключенным. */
    private static final float DISABLE_THRESHOLD = 350.0F;

    /** Доля от верхней границы, где начинается затухание (0 = сразу, 0.6 = в конце). */
    private static final float FOG_START_RATIO = 0.3F;

    // Ширина дальней полосы (за верхней границей) — чтобы горизонт тонул в тумане.
    private static final float FAR_BAND = 20.0F;

    // Цвет тумана (R, G, B), 0..1. Зелёный — в тон яда.
    private static final float FOG_R = 0.08F;
    private static final float FOG_G = 0.20F;
    private static final float FOG_B = 0.08F;

    // Скорость плавного перехода между тирами (0..1). Меньше — плавнее.
    private static final float SMOOTHING = 0.5F;

    // ====== Плавное сглаживание ======
    /** Текущая «сглаженная» дистанция тумана. -1 = отключено. */
    private static float smoothedDistance = -1F;

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void hardmode$poisonTierFog(Camera camera,
                                        int renderDistanceInChunks,
                                        DeltaTracker deltaTracker,
                                        float darkenWorldAmount,
                                        ClientLevel level,
                                        CallbackInfoReturnable<FogData> cir) {

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        int tier = getPoisonTier(player);
        float targetDistance = switch (tier) {
            case 2 -> TIER2_DISTANCE;
            case 3 -> TIER3_DISTANCE;
            case 4 -> TIER4_DISTANCE;
            default -> -1.0F;
        };

        // Время кадра в секундах. getRealtimeDeltaTicks() возвращает тики (1 tick = 50 ms).
        float dt = deltaTracker.getRealtimeDeltaTicks() / 20.0F;
        if (dt <= 0.0F || dt > 0.5F) dt = 1.0F / 60.0F; // защита от выбросов

        // ====== Обновляем smoothedDistance ======
        if (targetDistance < 0) {
            // Эффекта нет — туман должен уйти.
            if (smoothedDistance < 0) return; // уже отключён
            smoothedDistance = approach(smoothedDistance, INITIAL_DISTANCE, dt);
            if (smoothedDistance >= DISABLE_THRESHOLD) {
                smoothedDistance = -1.0F;
                return;
            }
        } else {
            // Эффект есть.
            if (smoothedDistance < 0) {
                // Только что появился — стартуем с далёкого значения, туман «придёт» сам.
                smoothedDistance = INITIAL_DISTANCE;
            }
            smoothedDistance = approach(smoothedDistance, targetDistance, dt);
        }

        if (smoothedDistance <= 0.5F) return;

        // ====== Применяем к FogData ======
        FogData fog = cir.getReturnValue();
        if (fog == null) return;

        float fogStart = smoothedDistance * FOG_START_RATIO;
        float fogEnd   = smoothedDistance;

        fog.color.set(FOG_R, FOG_G, FOG_B, 1.0F);

        fog.environmentalStart  = fogStart;
        fog.environmentalEnd    = fogEnd;

        fog.renderDistanceStart = fogEnd;
        fog.renderDistanceEnd   = fogEnd + FAR_BAND;

        fog.skyEnd   = fogEnd + FAR_BAND;
        fog.cloudEnd = fogEnd + FAR_BAND;
    }

    /** Экспоненциальный подход к цели. dt — время кадра в секундах. */
    private static float approach(float current, float target, float dt) {
        float decay = (float) Math.exp(-SMOOTHING_K * dt);
        return target + (current - target) * decay;
    }

    private static int getPoisonTier(LocalPlayer player) {
        if (player.hasEffect(ModEffects.LETHAL_POISON_FOUR))  return 4;
        if (player.hasEffect(ModEffects.LETHAL_POISON_TREE))  return 3;
        if (player.hasEffect(ModEffects.LETHAL_POISON_TWO))   return 2;
        if (player.hasEffect(ModEffects.LETHAL_POISON_ONE))   return 1;
        return 0;
    }
}
