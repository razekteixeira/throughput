package io.github.razekteixeira.throughput.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TrackedFactoryTest {
	private static final long CHEST_A = 1L;
	private static final long CHEST_B = 2L;

	private static ContainerReading partial(Map<String, Long> counts) {
		return new ContainerReading(counts, false, counts.isEmpty());
	}

	private static Map<Long, ContainerReading> readings(Object... posAndCounts) {
		Map<Long, ContainerReading> map = new HashMap<>();
		for (int i = 0; i < posAndCounts.length; i += 2) {
			@SuppressWarnings("unchecked")
			Map<String, Long> counts = (Map<String, Long>) posAndCounts[i + 1];
			map.put((Long) posAndCounts[i], partial(counts));
		}
		return map;
	}

	// AC1: insertions count as production, removals as consumption, per item.
	@Test
	void insertionIsProducedAndRemovalIsConsumed() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		factory.applySample(100, readings(CHEST_A, Map.of("coal", 64L)));
		Map<String, Long> delta = factory.applySample(101, readings(CHEST_A, Map.of("coal", 60L, "iron_ingot", 4L)));

		assertEquals(Map.of("coal", -4L, "iron_ingot", 4L), delta);
		Map<String, long[]> totals = factory.history().totals(Window.ONE_MINUTE, 101);
		assertEquals(4, totals.get("iron_ingot")[0], "produced");
		assertEquals(0, totals.get("iron_ingot")[1], "consumed");
		assertEquals(4, totals.get("coal")[1], "coal consumed is reported as a positive amount");
	}

	@Test
	void firstSampleIsOnlyABaseline() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		Map<String, Long> delta = factory.applySample(100, readings(CHEST_A, Map.of("diamond", 1000L)));
		assertTrue(delta.isEmpty(), "pre-existing stock must not count as production");
	}

	// AC2: transfers between two containers of the same factory cancel out.
	@Test
	void internalTransferHasZeroNetFlow() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		factory.add(CHEST_B);
		factory.applySample(10, readings(CHEST_A, Map.of("cobblestone", 30L), CHEST_B, Map.of()));
		Map<String, Long> delta = factory.applySample(11, readings(CHEST_A, Map.of("cobblestone", 10L), CHEST_B, Map.of("cobblestone", 20L)));

		assertTrue(delta.isEmpty(), "moved items are neither produced nor consumed: " + delta);
		assertTrue(factory.history().totals(Window.ONE_MINUTE, 11).isEmpty());
	}

	@Test
	void sameTransferWithOnlyOneSideTrackedIsVisible() {
		// Control for the test above: proves the zero there comes from cancellation, not from a dead tracker.
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		factory.applySample(10, readings(CHEST_A, Map.of("cobblestone", 30L)));
		Map<String, Long> delta = factory.applySample(11, readings(CHEST_A, Map.of("cobblestone", 10L)));
		assertEquals(Map.of("cobblestone", -20L), delta);
	}

	@Test
	void missingContainerDoesNotLookLikeConsumption() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		factory.applySample(10, readings(CHEST_A, Map.of("coal", 64L)));
		Map<String, Long> unloaded = factory.applySample(11, readings());
		Map<String, Long> reloaded = factory.applySample(12, readings(CHEST_A, Map.of("coal", 64L)));

		assertTrue(unloaded.isEmpty(), "an unloaded chunk is not 64 coal consumed");
		assertTrue(reloaded.isEmpty(), "reloading is not 64 coal produced");
		assertEquals(ContainerStatus.PARTIAL, factory.status(CHEST_A));
	}

	// AC4: time to empty.
	@Test
	void secondsToEmpty() {
		assertEquals(50, TrackedFactory.secondsToEmpty(100, -2.0));
		assertEquals(34, TrackedFactory.secondsToEmpty(100, -3.0), "rounds up so the alert is never optimistic");
		assertEquals(-1, TrackedFactory.secondsToEmpty(100, 0.0));
		assertEquals(-1, TrackedFactory.secondsToEmpty(100, 1.5));
		assertEquals(-1, TrackedFactory.secondsToEmpty(0, -1.0));
	}

	@Test
	void runningOutAlertUsesNetRateAndStock() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		long coal = 200;
		factory.applySample(0, readings(CHEST_A, Map.of("coal", coal, "iron_ingot", 0L)));
		for (int s = 1; s <= 20; s++) {
			coal -= 2; // net -2 per second
			factory.applySample(s, readings(CHEST_A, Map.of("coal", coal)));
		}
		List<Alert> alerts = factory.alerts(20);
		Alert alert = alerts.stream().filter(a -> a.kind() == Alert.Kind.RUNNING_OUT).findFirst().orElseThrow();
		assertEquals("coal", alert.item());
		assertEquals(80, alert.seconds(), "160 coal left at 2/s");
	}

	@Test
	void balancedItemDoesNotRaiseRunningOut() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		factory.add(CHEST_B);
		factory.applySample(0, readings(CHEST_A, Map.of("coal", 100L), CHEST_B, Map.of("coal", 100L)));
		// A is drained into B: consumption and production balance.
		factory.applySample(1, readings(CHEST_A, Map.of("coal", 90L), CHEST_B, Map.of("coal", 110L)));
		assertFalse(factory.alerts(1).stream().anyMatch(a -> a.kind() == Alert.Kind.RUNNING_OUT));
	}

	// AC5 at the core level: FULL and STARVED need to persist before alerting.
	@Test
	void blockedAndStarvedAlertsRespectThreshold() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		factory.add(CHEST_B);
		ContainerReading full = new ContainerReading(Map.of("stone", 1728L), true, false);
		ContainerReading empty = new ContainerReading(Map.of(), false, true);
		factory.applySample(0, Map.of(CHEST_A, full, CHEST_B, partial(Map.of("coal", 5L))));
		factory.applySample(1, Map.of(CHEST_A, full, CHEST_B, empty));
		assertTrue(factory.alerts(5).stream().noneMatch(a -> a.kind() == Alert.Kind.BLOCKED), "too early");

		for (int s = 2; s <= 15; s++) {
			factory.applySample(s, Map.of(CHEST_A, full, CHEST_B, empty));
		}
		List<Alert> alerts = factory.alerts(15);
		assertTrue(alerts.contains(new Alert(Alert.Kind.BLOCKED, CHEST_A, null, 15)), alerts.toString());
		assertTrue(alerts.contains(new Alert(Alert.Kind.RAN_DRY, CHEST_B, null, 14)), alerts.toString());
	}

	// AC3 on the path users see: rates divide by sampled time, not by the window length.
	@Test
	void topRatesDivideBySampledTimeAndRankHighestFirst() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		long iron = 0;
		long gold = 0;
		factory.applySample(0, readings(CHEST_A, Map.of()));
		for (int s = 1; s <= 30; s++) {
			iron += 2;
			gold += 1;
			factory.applySample(s, readings(CHEST_A, Map.of("iron_ingot", iron, "gold_ingot", gold)));
		}
		List<TrackedFactory.Rate> rates = factory.topRates(Window.TEN_MINUTES, 30, true, 8);
		assertEquals(List.of(new TrackedFactory.Rate("iron_ingot", 120.0), new TrackedFactory.Rate("gold_ingot", 60.0)), rates);
		assertEquals(1, factory.topRates(Window.TEN_MINUTES, 30, true, 1).size(), "limit");
		assertTrue(factory.topRates(Window.TEN_MINUTES, 30, false, 8).isEmpty(), "nothing consumed");
	}

	@Test
	void removedContainerStopsCountingAndCanBeReAddedAsFreshBaseline() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		factory.add(CHEST_B);
		factory.applySample(0, readings(CHEST_A, Map.of("coal", 10L), CHEST_B, Map.of("coal", 10L)));
		assertTrue(factory.remove(CHEST_B));
		assertFalse(factory.remove(CHEST_B), "already removed");
		Map<String, Long> delta = factory.applySample(1, readings(CHEST_A, Map.of("coal", 10L), CHEST_B, Map.of("coal", 500L)));
		assertTrue(delta.isEmpty(), "removed container is ignored: " + delta);
		assertEquals(Map.of("coal", 10L), factory.stock());

		factory.add(CHEST_B);
		assertTrue(factory.applySample(2, readings(CHEST_A, Map.of("coal", 10L), CHEST_B, Map.of("coal", 500L))).isEmpty(),
				"re-added container starts from a baseline, not from its old reading");
	}

	@Test
	void missingDurationStartsWhenFirstSeen() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		factory.applySample(500, readings());
		assertEquals(List.of(new Alert(Alert.Kind.MISSING, CHEST_A, null, 5)), factory.alerts(505));
	}

	@Test
	void positionsAreCapped() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		for (long p = 0; p < TrackedFactory.MAX_POSITIONS; p++) {
			assertTrue(factory.add(p));
		}
		assertTrue(factory.isFull());
		assertFalse(factory.add(-1L));
		assertEquals(TrackedFactory.MAX_POSITIONS, factory.positions().size());
	}

	@Test
	void containerThatWasAlwaysEmptyIsNotStarved() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(CHEST_A);
		ContainerReading empty = new ContainerReading(Map.of(), false, true);
		for (int s = 0; s <= 30; s++) {
			factory.applySample(s, Map.of(CHEST_A, empty));
		}
		assertTrue(factory.alerts(30).isEmpty());
	}
}
