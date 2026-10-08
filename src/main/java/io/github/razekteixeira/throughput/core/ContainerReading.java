package io.github.razekteixeira.throughput.core;

import java.util.Map;

/**
 * What one container held at a sample.
 *
 * @param counts item id to total amount
 * @param full   every slot holds its maximum amount, so nothing more can be inserted
 * @param empty  every slot is empty
 */
public record ContainerReading(Map<String, Long> counts, boolean full, boolean empty) {
	public ContainerStatus status() {
		if (full) {
			return ContainerStatus.FULL;
		}
		return empty ? ContainerStatus.EMPTY : ContainerStatus.PARTIAL;
	}
}
