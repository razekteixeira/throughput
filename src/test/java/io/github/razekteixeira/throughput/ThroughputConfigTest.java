package io.github.razekteixeira.throughput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.razekteixeira.throughput.core.Settings;

class ThroughputConfigTest {
	@Test
	void missingFileIsCreatedWithDefaults(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("config/throughput.json");
		String status = ThroughputConfig.load(file);
		assertTrue(status.startsWith("Wrote default settings"), status);
		String written = Files.readString(file);
		for (String option : new String[] {"sampleIntervalSeconds", "alertAfterSeconds", "runningOutHorizonMinutes",
				"maxContainersPerFactory", "maxAreaBlocks", "permissionLevels", "\"use\"", "\"manage\"", "\"coordinates\"", "\"admin\""}) {
			assertTrue(written.contains(option), "generated file documents " + option + ":\n" + written);
		}
		assertEquals(Settings.DEFAULTS, ThroughputConfig.get());
	}

	@Test
	void brokenFileKeepsPreviousSettingsAndIsNotOverwritten(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("throughput.json");
		Files.writeString(file, "{\"alertAfterSeconds\": 30}");
		ThroughputConfig.load(file);
		assertEquals(30, ThroughputConfig.get().alertAfterSeconds());

		String broken = "{\"alertAfterSeconds\": 5,, oops";
		Files.writeString(file, broken);
		String status = ThroughputConfig.load(file);

		assertTrue(status.startsWith("Could not read"), status);
		assertEquals(30, ThroughputConfig.get().alertAfterSeconds(), "previous settings stay active");
		assertEquals(broken, Files.readString(file), "the owner's file is never overwritten");
	}

	@Test
	void wrongTypeIsReportedNotIgnored(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("throughput.json");
		Files.writeString(file, "{\"maxAreaBlocks\": \"lots\"}");
		assertTrue(ThroughputConfig.load(file).startsWith("Could not read"));
	}
}
