package io.github.razekteixeira.throughput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class Throughput implements ModInitializer {
	public static final String MOD_ID = "throughput";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info(ThroughputConfig.load());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ThroughputCommands.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(ThroughputService::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(ThroughputService::onServerStopped);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ThroughputService.unwatch(handler.getPlayer().getUUID()));
		LOGGER.info("Throughput ready: /throughput (alias /flow)");
	}
}
