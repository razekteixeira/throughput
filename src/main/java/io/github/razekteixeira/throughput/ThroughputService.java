package io.github.razekteixeira.throughput;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import io.github.razekteixeira.throughput.core.ContainerReading;
import io.github.razekteixeira.throughput.core.ThroughputState;
import io.github.razekteixeira.throughput.core.SampleClock;
import io.github.razekteixeira.throughput.core.TrackedFactory;

/** Samples every factory once per second of game time and drives the action bar watchers. */
public final class ThroughputService {
	public static final int TICKS_PER_SAMPLE = 20;
	private static final int WATCH_EVERY_SECONDS = 2;

	private static final Map<UUID, String> WATCHERS = new ConcurrentHashMap<>();
	private static final SampleClock CLOCK = new SampleClock();

	private ThroughputService() {
	}

	public static ThroughputState state(MinecraftServer server) {
		return ThroughputSavedData.get(server).state();
	}

	/** Game time in seconds. Game time is saved with the world, so history survives restarts. */
	public static long nowSecond(MinecraftServer server) {
		return server.overworld().getGameTime() / TICKS_PER_SAMPLE;
	}

	public static void onServerTick(MinecraftServer server) {
		long gameTime = server.overworld().getGameTime();
		if (!CLOCK.isNewTick(gameTime)) {
			return;
		}
		ThroughputSavedData data = ThroughputSavedData.get(server);
		if (data.isReadOnly() || data.state().factories().isEmpty()) {
			return;
		}
		int interval = ThroughputConfig.get().sampleIntervalSeconds();
		long second = gameTime / TICKS_PER_SAMPLE;
		boolean sampled = false;
		for (Map.Entry<String, TrackedFactory> entry : data.state().factories().entrySet()) {
			if (SampleClock.isDue(entry.getKey(), gameTime, interval * TICKS_PER_SAMPLE)) {
				sample(server, entry.getValue(), second);
				sampled = true;
			}
		}
		if (sampled) {
			data.setDirty();
		}
		// Refresh the action bar about every two seconds, or once per interval when sampling is slower.
		if (gameTime % TICKS_PER_SAMPLE == 0 && second % Math.max(WATCH_EVERY_SECONDS, interval) == 0) {
			updateWatchers(server, second);
		}
	}

	/** Reads every container of the factory and records one sample at {@code second}. */
	public static Map<String, Long> sample(MinecraftServer server, TrackedFactory factory, long second) {
		long started = System.nanoTime();
		ServerLevel level = level(server, factory.dimension());
		Map<Long, ContainerReading> readings = new HashMap<>();
		if (level != null) {
			for (long packed : factory.positions()) {
				ContainerReading reading = ContainerSampler.read(level, BlockPos.of(packed));
				if (reading != null) {
					readings.put(packed, reading);
				}
			}
		}
		Map<String, Long> delta = factory.applySample(second, readings);
		factory.recordSampleCost(System.nanoTime() - started);
		return delta;
	}

	public static @Nullable ServerLevel level(MinecraftServer server, String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		if (id == null) {
			return null;
		}
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
		return server.getLevel(key);
	}

	public static void watch(UUID player, String factory) {
		WATCHERS.put(player, factory);
	}

	public static boolean unwatch(UUID player) {
		return WATCHERS.remove(player) != null;
	}

	/** Forgets per-session state so it never leaks into the next world in singleplayer. */
	public static void onServerStopped(MinecraftServer server) {
		WATCHERS.clear();
		CLOCK.reset();
	}

	private static void updateWatchers(MinecraftServer server, long second) {
		ThroughputState state = state(server);
		WATCHERS.forEach((uuid, name) -> {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player == null) {
				return;
			}
			state.get(name).ifPresentOrElse(
					factory -> player.sendOverlayMessage(ThroughputReports.ticker(name, factory, second)),
					() -> WATCHERS.remove(uuid));
		});
	}
}
