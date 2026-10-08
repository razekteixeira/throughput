package io.github.razekteixeira.throughput.core;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.mojang.serialization.Codec;

/** All factories on a server, keyed by name. */
public final class ThroughputState {
	private static final Pattern NAME = Pattern.compile("[a-z0-9_\\-]{1,32}");

	private final Map<String, TrackedFactory> factories;

	public ThroughputState() {
		this(new TreeMap<>());
	}

	private ThroughputState(Map<String, TrackedFactory> factories) {
		this.factories = factories;
	}

	public static boolean isValidName(String name) {
		return NAME.matcher(name).matches();
	}

	/** Creates a factory; empty if the name is invalid or taken. */
	public Optional<TrackedFactory> create(String name, String dimension) {
		if (!isValidName(name) || factories.containsKey(name)) {
			return Optional.empty();
		}
		TrackedFactory factory = new TrackedFactory(dimension);
		factories.put(name, factory);
		return Optional.of(factory);
	}

	public boolean delete(String name) {
		return factories.remove(name) != null;
	}

	public Optional<TrackedFactory> get(String name) {
		return Optional.ofNullable(factories.get(name));
	}

	public Map<String, TrackedFactory> factories() {
		return java.util.Collections.unmodifiableMap(factories);
	}

	public static final Codec<ThroughputState> CODEC = Codec.unboundedMap(Codec.STRING, TrackedFactory.CODEC)
			.xmap(map -> new ThroughputState(new TreeMap<>(map)), state -> state.factories);
}
