package io.github.razekteixeira.throughput.core;

import java.util.Locale;

/** Text helpers that render well in vanilla chat without any client mod. */
public final class Format {
	private static final char[] BARS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

	private Format() {
	}

	/** "12.5", "1.2k", "3.4M" with one decimal. */
	public static String amount(double value) {
		double abs = Math.abs(value);
		if (abs >= 1_000_000) {
			return String.format(Locale.ROOT, "%.1fM", value / 1_000_000);
		}
		if (abs >= 1_000) {
			return String.format(Locale.ROOT, "%.1fk", value / 1_000);
		}
		return String.format(Locale.ROOT, "%.1f", value);
	}

	/** "45s", "12m 30s", "3h 5m". */
	public static String duration(long seconds) {
		if (seconds < 60) {
			return seconds + "s";
		}
		if (seconds < 3600) {
			return (seconds / 60) + "m " + (seconds % 60) + "s";
		}
		return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
	}

	/**
	 * Unicode block sparkline of absolute values, compressed to {@code width} columns by summing
	 * neighbouring buckets. Zero columns render as the lowest bar so the line keeps its shape.
	 */
	public static String sparkline(long[] values, int width) {
		if (values.length == 0 || width <= 0) {
			return "";
		}
		int columns = Math.min(width, values.length);
		long[] summed = new long[columns];
		for (int i = 0; i < values.length; i++) {
			summed[(int) ((long) i * columns / values.length)] += Math.abs(values[i]);
		}
		long max = 0;
		for (long v : summed) {
			max = Math.max(max, v);
		}
		StringBuilder line = new StringBuilder(columns);
		for (long v : summed) {
			int level = max == 0 ? 0 : (int) Math.round((double) v * (BARS.length - 1) / max);
			line.append(BARS[level]);
		}
		return line.toString();
	}
}
