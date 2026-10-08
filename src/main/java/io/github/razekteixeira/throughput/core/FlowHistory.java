package io.github.razekteixeira.throughput.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-item produced and consumed totals at several time resolutions.
 *
 * <p>Each {@link Window} owns a ring of {@link #BUCKETS} buckets addressed by absolute bucket index
 * ({@code second / bucketSeconds}), so stale buckets are detected by index instead of being rolled
 * over explicitly, and gaps in sampling simply leave buckets empty.
 *
 * <p>A sample recorded at second {@code s} carries the flow accrued during {@code (s - 1, s]}.
 */
public final class FlowHistory {
	public static final int BUCKETS = 60;
	static final int PRODUCED = 0;
	static final int CONSUMED = 1;

	private final Ring[] rings = new Ring[Window.values().length];
	private long baselineSecond;

	public FlowHistory() {
		this(-1);
	}

	private FlowHistory(long baselineSecond) {
		this.baselineSecond = baselineSecond;
		for (Window window : Window.values()) {
			rings[window.ordinal()] = new Ring(window.bucketSeconds());
		}
	}

	/** Marks the first sample; time before it never counts towards rates. */
	public void markBaseline(long second) {
		if (baselineSecond < 0) {
			baselineSecond = second;
		}
	}

	public long baselineSecond() {
		return baselineSecond;
	}

	/** Records one sample: positive amounts are production, negative amounts consumption. */
	public void record(long second, Map<String, Long> delta) {
		for (Ring ring : rings) {
			ring.add(second, delta);
		}
	}

	/** Seconds of sampled time the window currently covers, never more than the time since the baseline. */
	public long coveredSeconds(Window window, long nowSecond) {
		if (baselineSecond < 0 || nowSecond <= baselineSecond) {
			return 0;
		}
		long current = nowSecond / window.bucketSeconds();
		long windowStart = (current - BUCKETS + 1) * window.bucketSeconds();
		long covered = nowSecond - Math.max(windowStart, 0) + 1;
		return Math.min(covered, nowSecond - baselineSecond);
	}

	/** Per-item {produced, consumed} totals inside the window. Consumed is reported as a positive number. */
	public Map<String, long[]> totals(Window window, long nowSecond) {
		Ring ring = rings[window.ordinal()];
		long current = nowSecond / window.bucketSeconds();
		Map<String, long[]> result = new HashMap<>();
		for (int slot = 0; slot < BUCKETS; slot++) {
			if (!ring.isLive(slot, current)) {
				continue;
			}
			for (Map.Entry<String, long[]> entry : ring.buckets[slot].entrySet()) {
				long[] sum = result.computeIfAbsent(entry.getKey(), k -> new long[2]);
				sum[PRODUCED] += entry.getValue()[PRODUCED];
				sum[CONSUMED] += entry.getValue()[CONSUMED];
			}
		}
		return result;
	}

	public enum Flow { PRODUCED, CONSUMED, NET }

	/** Flow of one item per bucket, oldest bucket first. Consumed is reported as a positive number. */
	public long[] series(String item, Window window, long nowSecond, Flow flow) {
		Ring ring = rings[window.ordinal()];
		long current = nowSecond / window.bucketSeconds();
		long[] series = new long[BUCKETS];
		for (int i = 0; i < BUCKETS; i++) {
			long index = current - BUCKETS + 1 + i;
			if (index < 0) {
				continue;
			}
			int slot = Math.floorMod(index, BUCKETS);
			if (ring.slotIndex[slot] == index) {
				long[] value = ring.buckets[slot].get(item);
				if (value != null) {
					series[i] = switch (flow) {
						case PRODUCED -> value[PRODUCED];
						case CONSUMED -> value[CONSUMED];
						case NET -> value[PRODUCED] - value[CONSUMED];
					};
				}
			}
		}
		return series;
	}

	private static final class Ring {
		final int bucketSeconds;
		final long[] slotIndex = new long[BUCKETS];
		@SuppressWarnings("unchecked")
		final Map<String, long[]>[] buckets = new Map[BUCKETS];

		Ring(int bucketSeconds) {
			this.bucketSeconds = bucketSeconds;
			Arrays.fill(slotIndex, -1);
			for (int i = 0; i < BUCKETS; i++) {
				buckets[i] = new HashMap<>();
			}
		}

		void add(long second, Map<String, Long> delta) {
			long index = second / bucketSeconds;
			int slot = Math.floorMod(index, BUCKETS);
			if (slotIndex[slot] != index) {
				slotIndex[slot] = index;
				buckets[slot].clear();
			}
			for (Map.Entry<String, Long> entry : delta.entrySet()) {
				long amount = entry.getValue();
				if (amount == 0) {
					continue;
				}
				long[] value = buckets[slot].computeIfAbsent(entry.getKey(), k -> new long[2]);
				if (amount > 0) {
					value[PRODUCED] += amount;
				} else {
					value[CONSUMED] -= amount;
				}
			}
		}

		boolean isLive(int slot, long currentIndex) {
			long index = slotIndex[slot];
			return index >= 0 && index <= currentIndex && index > currentIndex - BUCKETS;
		}
	}

	// Persistence: each ring is stored as its live buckets only.

	private record BucketData(long index, Map<String, List<Long>> items) {
		static final Codec<BucketData> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("index").forGetter(BucketData::index),
				Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf(2, 2)).fieldOf("items").forGetter(BucketData::items)
		).apply(i, BucketData::new));
	}

	private record HistoryData(long baseline, List<List<BucketData>> rings) {
		static final Codec<HistoryData> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("baseline").forGetter(HistoryData::baseline),
				BucketData.CODEC.listOf().listOf().fieldOf("rings").forGetter(HistoryData::rings)
		).apply(i, HistoryData::new));
	}

	public static final Codec<FlowHistory> CODEC = HistoryData.CODEC.xmap(FlowHistory::fromData, FlowHistory::toData);

	private HistoryData toData() {
		List<List<BucketData>> data = new ArrayList<>();
		for (Ring ring : rings) {
			List<BucketData> buckets = new ArrayList<>();
			for (int slot = 0; slot < BUCKETS; slot++) {
				if (ring.slotIndex[slot] < 0 || ring.buckets[slot].isEmpty()) {
					continue;
				}
				Map<String, List<Long>> items = new HashMap<>();
				ring.buckets[slot].forEach((item, v) -> items.put(item, List.of(v[PRODUCED], v[CONSUMED])));
				buckets.add(new BucketData(ring.slotIndex[slot], items));
			}
			data.add(buckets);
		}
		return new HistoryData(baselineSecond, data);
	}

	private static FlowHistory fromData(HistoryData data) {
		FlowHistory history = new FlowHistory(data.baseline());
		for (int r = 0; r < Math.min(data.rings().size(), history.rings.length); r++) {
			Ring ring = history.rings[r];
			for (BucketData bucket : data.rings().get(r)) {
				int slot = Math.floorMod(bucket.index(), BUCKETS);
				ring.slotIndex[slot] = bucket.index();
				bucket.items().forEach((item, v) -> ring.buckets[slot].put(item, new long[] {v.get(0), v.get(1)}));
			}
		}
		return history;
	}
}
