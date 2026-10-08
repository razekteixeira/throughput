package io.github.razekteixeira.throughput.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FormatTest {
	@Test
	void amounts() {
		assertEquals("0.0", Format.amount(0));
		assertEquals("12.5", Format.amount(12.5));
		assertEquals("999.9", Format.amount(999.94));
		assertEquals("1.2k", Format.amount(1234));
		assertEquals("3.4M", Format.amount(3_400_000));
	}

	@Test
	void durations() {
		assertEquals("45s", Format.duration(45));
		assertEquals("12m 30s", Format.duration(750));
		assertEquals("3h 5m", Format.duration(3 * 3600 + 5 * 60 + 9));
	}

	@Test
	void sparklineScalesToTheMaximumAndCompresses() {
		assertEquals("▁▅█", Format.sparkline(new long[] {0, 4, 8}, 3));
		// Six buckets into three columns: pairs are summed, so the shape is 2, 2, 8.
		assertEquals("\u2583\u2583\u2588", Format.sparkline(new long[] {1, 1, 1, 1, 4, 4}, 3));
		assertEquals("▁▁", Format.sparkline(new long[] {0, 0}, 5), "never wider than the data");
		assertEquals("█", Format.sparkline(new long[] {-5}, 1), "uses magnitude");
	}
}
