package io.github.razekteixeira.throughput.core;

public enum ContainerStatus {
	PARTIAL,
	FULL,
	EMPTY,
	/** Unloaded, broken, or no longer exposing item storage. */
	MISSING
}
