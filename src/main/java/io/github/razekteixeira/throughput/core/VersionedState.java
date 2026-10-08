package io.github.razekteixeira.throughput.core;

import java.util.Optional;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

/**
 * The saved form of {@link ThroughputState}, tagged with a format number.
 *
 * <p>Minecraft discards saved data that fails to decode and overwrites it with a fresh instance on
 * the next save. To never lose a world's statistics, this codec always decodes: data it cannot read
 * (a newer format after a downgrade, or corruption) is kept verbatim as a <em>quarantine</em>, encoded
 * back unchanged, and the mod refuses to modify it.
 */
public final class VersionedState {
	/** Bump with a migration in {@link #decode} whenever the saved shape changes. */
	public static final int FORMAT = 1;

	private final ThroughputState state;
	private final Dynamic<?> quarantine;
	private final String problem;

	private VersionedState(ThroughputState state, Dynamic<?> quarantine, String problem) {
		this.state = state;
		this.quarantine = quarantine;
		this.problem = problem;
	}

	public static VersionedState of(ThroughputState state) {
		return new VersionedState(state, null, null);
	}

	public ThroughputState state() {
		return state;
	}

	/** Why the saved data could not be used, if it was quarantined. */
	public Optional<String> problem() {
		return Optional.ofNullable(problem);
	}

	public boolean isReadOnly() {
		return problem != null;
	}

	/**
	 * State for a data file Minecraft could not even read (for example truncated by a crash during a
	 * save). Nothing is kept in memory, so encoding is refused: the file on disk must stay as it is.
	 */
	public static VersionedState unreadable(String problem) {
		return new VersionedState(new ThroughputState(), null, problem);
	}

	public static final Codec<VersionedState> CODEC = Codec.of(VersionedState::encode, VersionedState::decode);

	private static <T> DataResult<T> encode(VersionedState value, DynamicOps<T> ops, T prefix) {
		if (value.quarantine != null) {
			return DataResult.success(value.quarantine.convert(ops).getValue());
		}
		if (value.problem != null) {
			return DataResult.error(() -> "refusing to overwrite unreadable Throughput data: " + value.problem);
		}
		return ThroughputState.CODEC.encodeStart(ops, value.state).flatMap(factories -> ops.mapBuilder()
				.add("format", ops.createInt(FORMAT))
				.add("factories", factories)
				.build(prefix));
	}

	private static <T> DataResult<Pair<VersionedState, T>> decode(DynamicOps<T> ops, T input) {
		Dynamic<T> dynamic = new Dynamic<>(ops, input);
		int format = dynamic.get("format").asInt(0);
		VersionedState result;
		if (format != FORMAT) {
			result = quarantined(dynamic, "saved by data format " + format + ", this version reads format " + FORMAT);
		} else {
			// result() is empty on any error, so a partial result (which would silently drop the factories
			// that failed) is never used: anything short of a clean parse is quarantined. Exceptions are
			// caught too, because escaping one would make Minecraft discard and overwrite the file.
			try {
				DataResult<ThroughputState> parsed = ThroughputState.CODEC.parse(dynamic.get("factories").orElseEmptyMap());
				result = parsed.result()
						.map(VersionedState::of)
						.orElseGet(() -> quarantined(dynamic, "unreadable data: "
								+ parsed.error().map(DataResult.Error::message).orElse("unknown error")));
			} catch (RuntimeException e) {
				result = quarantined(dynamic, "unreadable data: " + e);
			}
		}
		return DataResult.success(Pair.of(result, ops.empty()));
	}

	private static VersionedState quarantined(Dynamic<?> raw, String problem) {
		return new VersionedState(new ThroughputState(), raw, problem);
	}
}
