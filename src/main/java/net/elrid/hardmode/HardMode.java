package net.elrid.hardmode;

import net.elrid.hardmode.command.ZonePositionCommand;
import net.elrid.hardmode.network.SafeZoneNetworking;
import net.elrid.hardmode.zone.ModEffects;
import net.elrid.hardmode.zone.SafeZoneManager;
import net.elrid.hardmode.zone.SafeZoneParticles;
import net.elrid.hardmode.zone.SafeZoneState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HardMode implements ModInitializer {
	public static final String MOD_ID = "hardmode";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Nullable
	private static MinecraftServer currentServer;  // храним сервер

	public static MinecraftServer getServer() {
		return currentServer;
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			currentServer = server;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			currentServer = null;
		});
		ServerLifecycleEvents.SERVER_STARTED.register(new WorldLoadListener());
		ServerPlayerEvents.JOIN.register(new PlayerJoinHandler());

		ModEffects.register();
		SafeZoneNetworking.register();
		SafeZoneManager.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ZonePositionCommand.register(dispatcher);
		});

		SleepHandler.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
