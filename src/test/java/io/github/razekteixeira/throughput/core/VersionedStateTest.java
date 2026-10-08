package io.github.razekteixeira.throughput.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class VersionedStateTest {
	private static JsonElement save(VersionedState state) {
		return VersionedState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
	}

	private static VersionedState load(JsonElement json) {
		return VersionedState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
	}

	private static VersionedState load(String json) {
		return load(JsonParser.parseString(json));
	}

	@Test
	void currentFormatRoundTrips() {
		ThroughputState state = new ThroughputState();
		TrackedFactory factory = state.create("smelter", "minecraft:overworld").orElseThrow();
		factory.add(7L);
		factory.applySample(1, Map.of(7L, new ContainerReading(Map.of("coal", 2L), false, false)));

		JsonElement json = save(VersionedState.of(state));
		assertEquals(VersionedState.FORMAT, json.getAsJsonObject().get("format").getAsInt());
		VersionedState loaded = load(json);
		assertFalse(loaded.isReadOnly());
		assertEquals(Map.of(7L, 7L).keySet(), loaded.state().get("smelter").orElseThrow().positions());
	}

	// L3: alerts that depend on container history survive a restart.
	@Test
	void ranDryAlertSurvivesRestart() {
		ThroughputState state = new ThroughputState();
		TrackedFactory factory = state.create("smelter", "minecraft:overworld").orElseThrow();
		factory.add(7L);
		factory.applySample(100, Map.of(7L, new ContainerReading(Map.of("raw_iron", 5L), false, false)));
		factory.applySample(101, Map.of(7L, new ContainerReading(Map.of(), false, true)));

		TrackedFactory restarted = load(save(VersionedState.of(state))).state().get("smelter").orElseThrow();
		// After the restart the container is read again, still empty.
		restarted.applySample(130, Map.of(7L, new ContainerReading(Map.of(), false, true)));

		assertTrue(restarted.alerts(130).contains(new Alert(Alert.Kind.RAN_DRY, 7L, null, 29)),
				"empty since second 101, not since the restart: " + restarted.alerts(130));
	}

	@Test
	void newerFormatIsKeptVerbatimAndReadOnly() {
		String future = "{\"format\":99,\"factories\":{\"x\":{\"something\":\"new\"}},\"extra\":[1,2,3]}";
		VersionedState loaded = load(future);

		assertTrue(loaded.isReadOnly());
		assertTrue(loaded.problem().orElseThrow().contains("format 99"));
		assertTrue(loaded.state().factories().isEmpty(), "nothing runs on data we cannot read");
		assertEquals(JsonParser.parseString(future), save(loaded), "saved back byte for byte, never overwritten");
	}

	@Test
	void corruptCurrentFormatIsKeptVerbatim() {
		String corrupt = "{\"format\":1,\"factories\":{\"smelter\":{\"dimension\":\"minecraft:overworld\",\"positions\":\"oops\",\"history\":{}}}}";
		VersionedState loaded = load(corrupt);

		assertTrue(loaded.isReadOnly());
		assertEquals(JsonParser.parseString(corrupt), save(loaded));
	}

	@Test
	void oneBadFactoryQuarantinesEverythingInsteadOfDroppingIt() {
		ThroughputState state = new ThroughputState();
		state.create("good", "minecraft:overworld").orElseThrow().add(1L);
		JsonElement json = save(VersionedState.of(state));
		json.getAsJsonObject().getAsJsonObject("factories").add("bad", JsonParser.parseString("{\"dimension\":5}"));

		VersionedState loaded = load(json);
		assertTrue(loaded.isReadOnly(), "a partial load would silently delete 'bad' on the next save");
		assertEquals(json, save(loaded));
	}

	@Test
	void unknownContainerStatusIsAnErrorNotAnException() {
		ThroughputState state = new ThroughputState();
		TrackedFactory factory = state.create("f", "minecraft:overworld").orElseThrow();
		factory.add(3L);
		factory.applySample(1, Map.of(3L, new ContainerReading(Map.of(), false, true)));
		JsonElement json = save(VersionedState.of(state));
		json.getAsJsonObject().getAsJsonObject("factories").getAsJsonObject("f").getAsJsonArray("containers")
				.get(0).getAsJsonObject().addProperty("status", "EXPLODED");

		VersionedState loaded = load(json);
		assertTrue(loaded.isReadOnly());
		assertEquals(json, save(loaded));
	}

	@Test
	void unreadableFileStateRefusesToBeWritten() {
		VersionedState locked = VersionedState.unreadable("could not read factories.dat");
		assertTrue(locked.isReadOnly());
		assertTrue(locked.problem().orElseThrow().contains("factories.dat"));
		assertTrue(VersionedState.CODEC.encodeStart(JsonOps.INSTANCE, locked).error().isPresent(),
				"encoding must fail so Minecraft never replaces the unreadable file");
	}
}
