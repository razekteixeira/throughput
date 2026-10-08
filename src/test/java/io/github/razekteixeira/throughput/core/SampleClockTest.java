package io.github.razekteixeira.throughput.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SampleClockTest {
	@Test
	void eachFactorySamplesExactlyOncePerInterval() {
		for (String name : new String[] {"smelter", "main_base", "a", "zz-top"}) {
			int due = 0;
			for (long t = 0; t < 200; t++) {
				if (SampleClock.isDue(name, t, 20)) {
					due++;
				}
			}
			assertEquals(10, due, name);
		}
		int slow = 0;
		for (long t = 0; t < 1000; t++) {
			if (SampleClock.isDue("smelter", t, 100)) {
				slow++;
			}
		}
		assertEquals(10, slow, "configured 5 second interval");
	}

	@Test
	void factoriesAreSpreadOverTicks() {
		Map<Integer, Integer> perTick = new HashMap<>();
		for (int i = 0; i < 200; i++) {
			perTick.merge(SampleClock.offset("factory_" + i, 20), 1, Integer::sum);
		}
		assertEquals(20, perTick.size(), "every tick of the second gets some factories");
		assertTrue(perTick.values().stream().allMatch(n -> n < 30), "no tick takes a pile-up: " + perTick);
	}

	@Test
	void frozenGameTimeIsProcessedOnlyOnce() {
		// /tick freeze: the tick event keeps firing with the same game time.
		SampleClock clock = new SampleClock();
		assertTrue(clock.isNewTick(40));
		for (int i = 0; i < 100; i++) {
			assertFalse(clock.isNewTick(40), "frozen tick " + i);
		}
		assertTrue(clock.isNewTick(41), "resumes after unfreezing");
	}

	@Test
	void resetAllowsANewWorldToStartAtTheSameGameTime() {
		SampleClock clock = new SampleClock();
		assertTrue(clock.isNewTick(0));
		clock.reset();
		assertTrue(clock.isNewTick(0));
	}
}
