package io.github.razekteixeira.throughput.core;

import java.util.Locale;
import java.util.Optional;

/** Statistics windows, Factorio style. Each window is one ring of {@link FlowHistory#BUCKETS} buckets. */
public enum Window {
	ONE_MINUTE("1m", 1),
	TEN_MINUTES("10m", 10),
	ONE_HOUR("1h", 60),
	TEN_HOURS("10h", 600);

	private final String label;
	private final int bucketSeconds;

	Window(String label, int bucketSeconds) {
		this.label = label;
		this.bucketSeconds = bucketSeconds;
	}

	public String label() {
		return label;
	}

	public int bucketSeconds() {
		return bucketSeconds;
	}

	public long seconds() {
		return (long) bucketSeconds * FlowHistory.BUCKETS;
	}

	public static Optional<Window> byLabel(String label) {
		String wanted = label.toLowerCase(Locale.ROOT);
		for (Window window : values()) {
			if (window.label.equals(wanted)) {
				return Optional.of(window);
			}
		}
		return Optional.empty();
	}
}
