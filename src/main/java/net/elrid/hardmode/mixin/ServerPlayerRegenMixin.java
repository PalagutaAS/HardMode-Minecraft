package net.elrid.hardmode.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerRegenMixin {

    // Порог, при котором регенерация вообще работает
    private static final int REGEN_FOOD_THRESHOLD = 14;
    private static final int FAST_REGEN_FOOD_THRESHOLD = 18;

    private static final int FAST_REGEN_COOLDOWN = 50;
    private static final int SLOW_REGEN_COOLDOWN = 100;

    private static final float REGEN_AMOUNT = 1.0f;

    private static final float REGEN_EXHAUSTION = 2.5f;

    private int regenCooldown = 0;

    @Inject(method = "tickRegeneration", at = @At("HEAD"), cancellable = true)
    private void customRegeneration(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        FoodData foodData = player.getFoodData();
        int foodLevel = foodData.getFoodLevel();

        // Если голод достаточен и здоровье не полное — регенерируем
        if (foodLevel >= REGEN_FOOD_THRESHOLD && player.getHealth() < player.getMaxHealth()) {
            // Выбираем кулдаун в зависимости от голода
            int currentCooldown = (foodLevel >= FAST_REGEN_FOOD_THRESHOLD)
                    ? FAST_REGEN_COOLDOWN
                    : SLOW_REGEN_COOLDOWN;

            regenCooldown++;
            if (regenCooldown >= currentCooldown) {
                regenCooldown = 0;
                player.heal(REGEN_AMOUNT);
                foodData.addExhaustion(REGEN_EXHAUSTION);
                System.out.println("[HardMode] Регенерация: +1 HP (голод " + foodLevel
                        + ", кулдаун " + currentCooldown + " тиков)");
            }
        } else {
            regenCooldown = 0;
        }

        // Отменяем ванильную логику регенерации
        ci.cancel();
    }
}