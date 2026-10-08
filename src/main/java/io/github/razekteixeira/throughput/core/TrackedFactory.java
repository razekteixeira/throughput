package io.github.razekteixeira.throughput.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A named group of containers whose combined contents are sampled as one factory.
 *
 * <p>Flow is computed per container, between two consecutive readings of that same container, then
 * summed. Items moved between two containers of the factory therefore cancel out, and a container
 * that disappears (chunk unloaded, block broken) contributes nothing instead of looking like a
 * sudden mass consumption.
 */
public final class TrackedFactory {
	/** Default: a state must last this long before it raises a container alert. */
	public static final long ALERT_AFTER_SECONDS = 10;
	/** Default: only items that run out within this horizon raise an alert. */
	public static final long RUNNING_OUT_HORIZON_SECONDS = 3600;
	/** Hard ceiling per factory; servers set their own lower limit in {@link Settings}. */
	public static final int MAX_POSITIONS = 65_536;

	private final String dimension;
	private final Set<Long> positions;
	private final FlowHistory history;
	private final Map<Long, Tracker> trackers = new HashMap<>();
	/** Wall time the last sample took, for server owners; not persisted. */
	private long lastSampleNanos = -1;

	public TrackedFactory(String dimension) {
		this(dimension, new LinkedHashSet<>(), new FlowHistory(), List.of());
	}

	private TrackedFactory(String dimension, Set<Long> positions, FlowHistory history, List<ContainerMemory> memories) {
		this.dimension = dimension;
		this.positions = positions;
		this.history = history;
		for (ContainerMemory memory : memories) {
			if (positions.contains(memory.pos())) {
				trackers.put(memory.pos(), new Tracker(memory));
			}
		}
	}

	public String dimension() {
		return dimension;
	}

	/** Read-only view, in insertion order. */
	public Set<Long> positions() {
		return Collections.unmodifiableSet(positions);
	}

	public FlowHistory history() {
		return history;
	}

	/** Adds a container; false if it was already tracked or the factory is at {@link #MAX_POSITIONS}. */
	public boolean add(long pos) {
		return !isFull() && positions.add(pos);
	}

	public long lastSampleNanos() {
		return lastSampleNanos;
	}

	public void recordSampleCost(long nanos) {
		lastSampleNanos = nanos;
	}

	public boolean isFull() {
		return positions.size() >= MAX_POSITIONS;
	}

	public boolean remove(long pos) {
		trackers.remove(pos);
		return positions.remove(pos);
	}

	/**
	 * Applies one sample.
	 *
	 * @param readings packed position to reading; positions without an entry are treated as missing
	 * @return the net delta recorded, item id to amount (positive produced, negative consumed)
	 */
	public Map<String, Long> applySample(long second, Map<Long, ContainerReading> readings) {
		history.markBaseline(second);
		Map<String, Long> delta = new HashMap<>();
		for (long pos : positions) {
			Tracker tracker = trackers.computeIfAbsent(pos, p -> new Tracker());
			ContainerReading reading = readings.get(pos);
			if (reading == null) {
				tracker.update(second, ContainerStatus.MISSING, null);
				continue;
			}
			if (tracker.previous != null) {
				addDelta(delta, tracker.previous, reading.counts());
			}
			tracker.update(second, reading.status(), reading.counts());
		}
		delta.values().removeIf(v -> v == 0);
		history.record(second, delta);
		return delta;
	}

	private static void addDelta(Map<String, Long> delta, Map<String, Long> before, Map<String, Long> after) {
		after.forEach((item, amount) -> delta.merge(item, amount, Long::sum));
		before.forEach((item, amount) -> delta.merge(item, -amount, Long::sum));
	}

	/** Current stock per item across all containers present at the last sample. */
	public Map<String, Long> stock() {
		Map<String, Long> stock = new HashMap<>();
		for (Tracker tracker : trackers.values()) {
			if (tracker.previous != null) {
				tracker.previous.forEach((item, amount) -> stock.merge(item, amount, Long::sum));
			}
		}
		return stock;
	}

	public ContainerStatus status(long pos) {
		Tracker tracker = trackers.get(pos);
		return tracker == null ? ContainerStatus.MISSING : tracker.status;
	}

	/**
	 * Seconds until the stock of {@code item} runs out at the current net rate, or -1 if it is not
	 * being drained.
	 */
	public static long secondsToEmpty(long stock, double netPerSecond) {
		if (netPerSecond >= 0 || stock <= 0) {
			return -1;
		}
		return (long) Math.ceil(stock / -netPerSecond);
	}

	public List<Alert> alerts(long nowSecond) {
		return alerts(nowSecond, ALERT_AFTER_SECONDS, RUNNING_OUT_HORIZON_SECONDS);
	}

	public List<Alert> alerts(long nowSecond, long alertAfterSeconds, long runningOutHorizonSeconds) {
		List<Alert> alerts = new ArrayList<>();
		for (long pos : positions) {
			Tracker tracker = trackers.get(pos);
			if (tracker == null) {
				continue;
			}
			long duration = nowSecond - tracker.since;
			switch (tracker.status) {
				case FULL -> {
					if (duration >= alertAfterSeconds) {
						alerts.add(new Alert(Alert.Kind.BLOCKED, pos, null, duration));
					}
				}
				case EMPTY -> {
					if (tracker.everHeldItems && duration >= alertAfterSeconds) {
						alerts.add(new Alert(Alert.Kind.RAN_DRY, pos, null, duration));
					}
				}
				case MISSING -> alerts.add(new Alert(Alert.Kind.MISSING, pos, null, duration));
				case PARTIAL -> { }
			}
		}
		long covered = history.coveredSeconds(Window.ONE_MINUTE, nowSecond);
		if (covered > 0) {
			Map<String, long[]> totals = history.totals(Window.ONE_MINUTE, nowSecond);
			stock().forEach((item, amount) -> {
				long[] flow = totals.get(item);
				if (flow == null) {
					return;
				}
				double netPerSecond = (double) (flow[FlowHistory.PRODUCED] - flow[FlowHistory.CONSUMED]) / covered;
				long left = secondsToEmpty(amount, netPerSecond);
				if (left >= 0 && left <= runningOutHorizonSeconds) {
					alerts.add(new Alert(Alert.Kind.RUNNING_OUT, null, item, left));
				}
			});
		}
		alerts.sort(Comparator.comparing(Alert::kind).thenComparingLong(Alert::seconds));
		return alerts;
	}

	/** One row of a rate table. */
	public record Rate(String item, double perMinute) {
	}

	/**
	 * Items ranked by produced (or consumed) amount per minute in the window, highest first. Rates
	 * divide by the time actually sampled, so a factory created two minutes ago is not diluted
	 * across a ten minute window.
	 */
	public List<Rate> topRates(Window window, long nowSecond, boolean produced, int limit) {
		long covered = history.coveredSeconds(window, nowSecond);
		if (covered == 0) {
			return List.of();
		}
		List<Rate> rates = new ArrayList<>();
		for (Map.Entry<String, long[]> entry : history.totals(window, nowSecond).entrySet()) {
			long amount = entry.getValue()[produced ? FlowHistory.PRODUCED : FlowHistory.CONSUMED];
			if (amount > 0) {
				rates.add(new Rate(entry.getKey(), amount * 60.0 / covered));
			}
		}
		rates.sort(Comparator.comparingDouble(Rate::perMinute).reversed().thenComparing(Rate::item));
		return rates.size() > limit ? List.copyOf(rates.subList(0, limit)) : rates;
	}

	private static final class Tracker {
		/** Never persisted: after a restart the first reading is a fresh baseline. */
		Map<String, Long> previous;
		ContainerStatus status;
		long since;
		boolean everHeldItems;

		Tracker() {
		}

		Tracker(ContainerMemory memory) {
			status = memory.status();
			since = memory.since();
			everHeldItems = memory.everHeldItems();
		}

		void update(long second, ContainerStatus newStatus, Map<String, Long> counts) {
			if (newStatus != status) {
				status = newStatus;
				since = second;
			}
			previous = counts;
			if (counts != null && !counts.isEmpty()) {
				everHeldItems = true;
			}
		}
	}

	/** What a container's alerts need to survive a restart: its state, since when, and whether it ever held items. */
	private record ContainerMemory(long pos, ContainerStatus status, long since, boolean everHeldItems) {
		static final Codec<ContainerStatus> STATUS_CODEC = Codec.STRING.comapFlatMap(name -> {
			try {
				return DataResult.success(ContainerStatus.valueOf(name));
			} catch (IllegalArgumentException e) {
				return DataResult.error(() -> "unknown container status " + name);
			}
		}, ContainerStatus::name);
		static final Codec<ContainerMemory> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("pos").forGetter(ContainerMemory::pos),
				STATUS_CODEC.fieldOf("status").forGetter(ContainerMemory::status),
				Codec.LONG.fieldOf("since").forGetter(ContainerMemory::since),
				Codec.BOOL.fieldOf("held").forGetter(ContainerMemory::everHeldItems)
		).apply(i, ContainerMemory::new));
	}

	private List<ContainerMemory> memories() {
		List<ContainerMemory> memories = new ArrayList<>();
		trackers.forEach((pos, t) -> {
			if (t.status != null) {
				memories.add(new ContainerMemory(pos, t.status, t.since, t.everHeldItems));
			}
		});
		return memories;
	}

	public static final Codec<TrackedFactory> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(TrackedFactory::dimension),
			Codec.LONG.listOf().fieldOf("positions").forGetter(f -> List.copyOf(f.positions)),
			FlowHistory.CODEC.fieldOf("history").forGetter(TrackedFactory::history),
			ContainerMemory.CODEC.listOf().optionalFieldOf("containers", List.of()).forGetter(TrackedFactory::memories)
	).apply(i, (dimension, positions, history, memories) -> new TrackedFactory(dimension, new LinkedHashSet<>(positions), history, memories)));
}
