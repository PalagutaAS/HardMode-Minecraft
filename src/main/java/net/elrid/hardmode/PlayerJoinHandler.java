package net.elrid.hardmode;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;

public class PlayerJoinHandler implements ServerPlayerEvents.Join {

    // Максимальный уровень насыщения
    private static final float MAX_SATURATION = 5.0f;

    @Override
    public void onJoin(ServerPlayer player) {
        var hungerManager = player.getFoodData();

        // Проверяем и ограничиваем насыщение
        if (hungerManager.getSaturationLevel() > MAX_SATURATION) {
            hungerManager.setSaturation(MAX_SATURATION);
            System.out.println("Насыщение игрока " + player.getName().getString() +
                    " ограничено до " + MAX_SATURATION);
        }
    }
}