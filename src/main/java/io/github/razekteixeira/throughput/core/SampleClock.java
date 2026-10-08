package io.github.razekteixeira.throughput.core;

/**
 * Decides which factories sample on which tick.
 *
 * <p>Each factory samples on its own tick within the interval (a stable offset from its name), so
 * many large factories never pile their cost onto one tick. A factory still reads all of its
 * containers on the same tick, which keeps transfers between them cancelling out.
 *
 * <p>Server tick events keep firing while game time stands still (for example under
 * {@code /tick freeze}), so each game time is processed only once.
 */
public final class SampleClock {
	private long lastGameTime = Long.MIN_VALUE;

	/** True the first time a game time is seen; false while time is frozen on it. */
	public boolean isNewTick(long gameTime) {
		if (gameTime == lastGameTime) {
			return false;
		}
		lastGameTime = gameTime;
		return true;
	}

	/** Whether the factory with this name samples on this game time. */
	public static boolean isDue(String factoryName, long gameTime, int ticksPerSample) {
		return Math.floorMod(gameTime - offset(factoryName, ticksPerSample), ticksPerSample) == 0;
	}

	static int offset(String factoryName, int ticksPerSample) {
		return Math.floorMod(factoryName.hashCode(), ticksPerSample);
	}

	public void reset() {
		lastGameTime = Long.MIN_VALUE;
	}
}
