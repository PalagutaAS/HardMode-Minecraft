package net.elrid.hardmode;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.server.level.ServerPlayer;


public class SleepHandler {
    public static void register() {
        EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
            if (entity instanceof ServerPlayer player && player.isSleepingLongEnough()) {
                System.out.println("Игрок спал долго, добавили истощение" + player.getName().getString() +
                        "+ 15 и похилили на 2");

                player.heal(2F);
                player.getFoodData().addExhaustion(15F);
            }
        });
    }
}