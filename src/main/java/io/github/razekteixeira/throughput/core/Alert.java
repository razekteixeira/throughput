package io.github.razekteixeira.throughput.core;

/**
 * Something in a factory that needs attention.
 *
 * @param pos     packed block position of the container, or {@code null} for item alerts
 * @param item    item id for {@link Kind#RUNNING_OUT}, otherwise {@code null}
 * @param seconds how long the state has lasted, or for {@link Kind#RUNNING_OUT} the time left
 */
public record Alert(Kind kind, Long pos, String item, long seconds) {
	public enum Kind {
		/** Container full: whatever feeds it is backing up. */
		BLOCKED,
		/**
		 * Container emptied after having held items. Whatever it feeds stalls once its own buffers
		 * drain; a hopper below a chest can hold the whole stock, so this is not proof of idleness.
		 */
		RAN_DRY,
		/** Container unloaded or removed. */
		MISSING,
		/** Net consumption will empty the stock soon. */
		RUNNING_OUT
	}
}
