package io.github.razekteixeira.throughput.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class FlowHistoryTest {
	private static FlowHistory steadyProduction(long fromSecond, long toSecond, long perSecond) {
		FlowHistory history = new FlowHistory();
		history.markBaseline(fromSecond);
		for (long s = fromSecond + 1; s <= toSecond; s++) {
			history.record(s, Map.of("iron_ingot", perSecond));
		}
		return history;
	}

	// AC3: while a window is not yet full, rates divide by the time actually sampled.
	@Test
	void coveredSecondsGrowsUntilTheWindowIsFull() {
		FlowHistory history = steadyProduction(1000, 1030, 1);
		assertEquals(30, history.coveredSeconds(Window.ONE_MINUTE, 1030));
		assertEquals(30, history.coveredSeconds(Window.TEN_HOURS, 1030));

		FlowHistory full = steadyProduction(1000, 1500, 1);
		assertEquals(60, full.coveredSeconds(Window.ONE_MINUTE, 1500));
		assertEquals(500, full.coveredSeconds(Window.TEN_MINUTES, 1500));
	}

	@Test
	void ratesAreExactForASteadyStream() {
		FlowHistory history = steadyProduction(0, 5000, 2);
		for (Window window : Window.values()) {
			long covered = history.coveredSeconds(window, 5000);
			long produced = history.totals(window, 5000).get("iron_ingot")[0];
			assertEquals(2.0, (double) produced / covered, 1e-9, window.label() + " must report exactly 2 per second");
		}
	}

	@Test
	void coarseTiersAgreeWithFineTiersOverTheSameSpan() {
		FlowHistory history = new FlowHistory();
		history.markBaseline(0);
		long expectedLastMinute = 0;
		for (long s = 1; s <= 600; s++) {
			long amount = (s * 7) % 13; // irregular but deterministic
			history.record(s, Map.of("gear", amount));
			if (s > 540) {
				expectedLastMinute += amount;
			}
		}
		long total = 0;
		long fromSecondTen = 0;
		for (long s = 1; s <= 600; s++) {
			total += (s * 7) % 13;
			if (s >= 10) {
				fromSecondTen += (s * 7) % 13;
			}
		}
		assertEquals(expectedLastMinute, history.totals(Window.ONE_MINUTE, 600).get("gear")[0]);
		// 10 s buckets: at second 600 the 60 bucket ring starts at bucket 1, i.e. second 10,
		// which is exactly what coveredSeconds reports, so the rate stays exact.
		assertEquals(fromSecondTen, history.totals(Window.TEN_MINUTES, 600).get("gear")[0]);
		assertEquals(591, history.coveredSeconds(Window.TEN_MINUTES, 600));
		assertEquals(total, history.totals(Window.ONE_HOUR, 600).get("gear")[0]);
		assertEquals(total, history.totals(Window.TEN_HOURS, 600).get("gear")[0]);
	}

	@Test
	void oldBucketsFallOutOfTheWindow() {
		FlowHistory history = new FlowHistory();
		history.markBaseline(0);
		history.record(5, Map.of("coal", 100L));
		assertEquals(100, history.totals(Window.ONE_MINUTE, 64).get("coal")[0], "second 5 is still inside at 64");
		assertTrue(history.totals(Window.ONE_MINUTE, 65).isEmpty(), "second 5 left the 1 minute window at 65");
		assertEquals(100, history.totals(Window.TEN_MINUTES, 65).get("coal")[0], "but stays in the 10 minute window");
	}

	@Test
	void ringSlotsAreReusedWithoutLeakingOldData() {
		FlowHistory history = new FlowHistory();
		history.markBaseline(0);
		history.record(10, Map.of("coal", 100L));
		history.record(70, Map.of("coal", 1L)); // same ring slot as second 10 in the 1 minute ring
		assertEquals(1, history.totals(Window.ONE_MINUTE, 70).get("coal")[0]);
	}

	@Test
	void seriesSplitsProducedConsumedAndNet() {
		FlowHistory history = new FlowHistory();
		history.markBaseline(0);
		history.record(58, Map.of("coal", 5L));
		history.record(59, Map.of("coal", -3L));
		long[] produced = history.series("coal", Window.ONE_MINUTE, 59, FlowHistory.Flow.PRODUCED);
		long[] consumed = history.series("coal", Window.ONE_MINUTE, 59, FlowHistory.Flow.CONSUMED);
		long[] net = history.series("coal", Window.ONE_MINUTE, 59, FlowHistory.Flow.NET);
		assertEquals(5, produced[58]);
		assertEquals(3, consumed[59]);
		assertEquals(-3, net[59]);
		assertEquals(0, produced[59]);
	}

	// AC6: persistence round trip keeps every tier and the baseline.
	@Test
	void codecRoundTripPreservesHistory() {
		FlowHistory original = steadyProduction(100, 2000, 3);
		original.record(2000, Map.of("coal", -7L));
		JsonElement json = FlowHistory.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
		FlowHistory decoded = FlowHistory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

		assertEquals(original.baselineSecond(), decoded.baselineSecond());
		for (Window window : Window.values()) {
			assertEquals(original.coveredSeconds(window, 2000), decoded.coveredSeconds(window, 2000));
			Map<String, long[]> before = original.totals(window, 2000);
			Map<String, long[]> after = decoded.totals(window, 2000);
			assertEquals(before.keySet(), after.keySet());
			before.forEach((item, v) -> assertArrayEquals(v, after.get(item), window.label() + " " + item));
			assertArrayEquals(original.series("iron_ingot", window, 2000, FlowHistory.Flow.NET),
					decoded.series("iron_ingot", window, 2000, FlowHistory.Flow.NET));
		}
	}

	@Test
	void stateRoundTrip() {
		ThroughputState state = new ThroughputState();
		TrackedFactory smelter = state.create("smelter", "minecraft:overworld").orElseThrow();
		smelter.add(42L);
		smelter.add(-7L);
		smelter.applySample(1, Map.of(42L, new ContainerReading(Map.of("coal", 3L), false, false)));
		smelter.applySample(2, Map.of(42L, new ContainerReading(Map.of("coal", 1L), false, false)));

		JsonElement json = ThroughputState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
		ThroughputState decoded = ThroughputState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		TrackedFactory copy = decoded.get("smelter").orElseThrow();

		assertEquals("minecraft:overworld", copy.dimension());
		assertEquals(smelter.positions(), copy.positions());
		assertEquals(2, copy.history().totals(Window.ONE_MINUTE, 2).get("coal")[1]);
	}

	@Test
	void factoryNamesAreValidated() {
		ThroughputState state = new ThroughputState();
		assertTrue(state.create("main_base-2", "minecraft:overworld").isPresent());
		assertTrue(state.create("main_base-2", "minecraft:overworld").isEmpty(), "duplicate");
		assertTrue(state.create("Has Space", "minecraft:overworld").isEmpty());
		assertTrue(state.create("", "minecraft:overworld").isEmpty());
		assertTrue(state.create("x".repeat(33), "minecraft:overworld").isEmpty());
	}
}
