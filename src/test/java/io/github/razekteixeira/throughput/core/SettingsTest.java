package io.github.razekteixeira.throughput.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class SettingsTest {
	private static Settings parse(String json) {
		return Settings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
	}

	@Test
	void emptyFileMeansDefaults() {
		assertEquals(Settings.DEFAULTS, parse("{}"));
	}

	@Test
	void partialFileKeepsOtherDefaults() {
		Settings s = parse("{\"sampleIntervalSeconds\": 5, \"permissionLevels\": {\"manage\": 3}}");
		assertEquals(5, s.sampleIntervalSeconds());
		assertEquals(3, s.permissionLevels().manage());
		assertEquals(Settings.DEFAULTS.permissionLevels().use(), s.permissionLevels().use());
		assertEquals(Settings.DEFAULTS.maxAreaBlocks(), s.maxAreaBlocks());
	}

	@Test
	void outOfRangeValuesAreClampedWithAWarningEach() {
		Settings.Checked checked = parse("{\"sampleIntervalSeconds\": 0, \"maxContainersPerFactory\": 999999,"
				+ " \"permissionLevels\": {\"admin\": 9}}").checked();
		assertEquals(1, checked.settings().sampleIntervalSeconds());
		assertEquals(TrackedFactory.MAX_POSITIONS, checked.settings().maxContainersPerFactory());
		assertEquals(4, checked.settings().permissionLevels().admin());
		assertEquals(3, checked.warnings().size(), checked.warnings().toString());
	}

	@Test
	void defaultsNeedNoCorrection() {
		assertTrue(Settings.DEFAULTS.checked().warnings().isEmpty());
		assertEquals(3600, Settings.DEFAULTS.runningOutHorizonSeconds());
	}

	@Test
	void configuredThresholdsChangeAlerts() {
		TrackedFactory factory = new TrackedFactory("minecraft:overworld");
		factory.add(1L);
		ContainerReading full = new ContainerReading(java.util.Map.of("stone", 64L), true, false);
		factory.applySample(0, java.util.Map.of(1L, full));
		factory.applySample(5, java.util.Map.of(1L, full));
		assertTrue(factory.alerts(5).isEmpty(), "default threshold is 10 s");
		assertEquals(1, factory.alerts(5, 5, 3600).size(), "a 5 s threshold fires");
	}
}
