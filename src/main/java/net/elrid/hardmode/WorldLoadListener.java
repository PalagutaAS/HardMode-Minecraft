package net.elrid.hardmode;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;

public class WorldLoadListener implements ServerLifecycleEvents.ServerStarted {

    @Override
    public void onServerStarted(MinecraftServer server) {
        // Получаем правило и устанавливаем его в false
        //server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        //HardMode.LOGGER.info("Правило установлено в " + server.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION));
    }
}