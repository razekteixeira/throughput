package io.github.razekteixeira.throughput.core;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Server owner settings, read from {@code config/throughput.json}. Missing fields take their
 * defaults and out-of-range values are clamped, so a partial or hand-edited file always works.
 */
public record Settings(
		int sampleIntervalSeconds,
		int alertAfterSeconds,
		int runningOutHorizonMinutes,
		int maxContainersPerFactory,
		int maxAreaBlocks,
		PermissionLevels permissionLevels) {

	/**
	 * Vanilla permission levels used when no permissions mod is installed. With one (e.g. LuckPerms),
	 * the nodes {@code throughput.use}, {@code throughput.manage}, {@code throughput.coordinates} and
	 * {@code throughput.admin} take precedence.
	 */
	public record PermissionLevels(int use, int manage, int coordinates, int admin) {
		public static final PermissionLevels DEFAULTS = new PermissionLevels(0, 2, 2, 3);

		static final Codec<PermissionLevels> WRITE_CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("use").forGetter(PermissionLevels::use),
				Codec.INT.fieldOf("manage").forGetter(PermissionLevels::manage),
				Codec.INT.fieldOf("coordinates").forGetter(PermissionLevels::coordinates),
				Codec.INT.fieldOf("admin").forGetter(PermissionLevels::admin)
		).apply(i, PermissionLevels::new));

		static final Codec<PermissionLevels> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("use", DEFAULTS.use).forGetter(PermissionLevels::use),
				Codec.INT.optionalFieldOf("manage", DEFAULTS.manage).forGetter(PermissionLevels::manage),
				Codec.INT.optionalFieldOf("coordinates", DEFAULTS.coordinates).forGetter(PermissionLevels::coordinates),
				Codec.INT.optionalFieldOf("admin", DEFAULTS.admin).forGetter(PermissionLevels::admin)
		).apply(i, PermissionLevels::new));
	}

	public static final Settings DEFAULTS = new Settings(1, 10, 60, 4096, 32768, PermissionLevels.DEFAULTS);

	/**
	 * Writes every field, including defaults, so the generated file documents all options.
	 * ({@code optionalFieldOf} would omit values equal to their default.)
	 */
	public static final Codec<Settings> WRITE_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("sampleIntervalSeconds").forGetter(Settings::sampleIntervalSeconds),
			Codec.INT.fieldOf("alertAfterSeconds").forGetter(Settings::alertAfterSeconds),
			Codec.INT.fieldOf("runningOutHorizonMinutes").forGetter(Settings::runningOutHorizonMinutes),
			Codec.INT.fieldOf("maxContainersPerFactory").forGetter(Settings::maxContainersPerFactory),
			Codec.INT.fieldOf("maxAreaBlocks").forGetter(Settings::maxAreaBlocks),
			PermissionLevels.WRITE_CODEC.fieldOf("permissionLevels").forGetter(Settings::permissionLevels)
	).apply(i, Settings::new));

	/** Reads leniently: every field is optional. */
	public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("sampleIntervalSeconds", DEFAULTS.sampleIntervalSeconds).forGetter(Settings::sampleIntervalSeconds),
			Codec.INT.optionalFieldOf("alertAfterSeconds", DEFAULTS.alertAfterSeconds).forGetter(Settings::alertAfterSeconds),
			Codec.INT.optionalFieldOf("runningOutHorizonMinutes", DEFAULTS.runningOutHorizonMinutes).forGetter(Settings::runningOutHorizonMinutes),
			Codec.INT.optionalFieldOf("maxContainersPerFactory", DEFAULTS.maxContainersPerFactory).forGetter(Settings::maxContainersPerFactory),
			Codec.INT.optionalFieldOf("maxAreaBlocks", DEFAULTS.maxAreaBlocks).forGetter(Settings::maxAreaBlocks),
			PermissionLevels.CODEC.optionalFieldOf("permissionLevels", PermissionLevels.DEFAULTS).forGetter(Settings::permissionLevels)
	).apply(i, Settings::new));

	/** Settings with every value clamped to its allowed range, plus one warning per clamped value. */
	public record Checked(Settings settings, List<String> warnings) {
	}

	public Checked checked() {
		List<String> warnings = new ArrayList<>();
		PermissionLevels p = permissionLevels;
		Settings clamped = new Settings(
				clamp("sampleIntervalSeconds", sampleIntervalSeconds, 1, 60, warnings),
				clamp("alertAfterSeconds", alertAfterSeconds, 1, 86_400, warnings),
				clamp("runningOutHorizonMinutes", runningOutHorizonMinutes, 1, 10_080, warnings),
				clamp("maxContainersPerFactory", maxContainersPerFactory, 1, TrackedFactory.MAX_POSITIONS, warnings),
				clamp("maxAreaBlocks", maxAreaBlocks, 1, 1_000_000, warnings),
				new PermissionLevels(
						clamp("permissionLevels.use", p.use(), 0, 4, warnings),
						clamp("permissionLevels.manage", p.manage(), 0, 4, warnings),
						clamp("permissionLevels.coordinates", p.coordinates(), 0, 4, warnings),
						clamp("permissionLevels.admin", p.admin(), 0, 4, warnings)));
		return new Checked(clamped, List.copyOf(warnings));
	}

	private static int clamp(String name, int value, int min, int max, List<String> warnings) {
		int clamped = Math.max(min, Math.min(max, value));
		if (clamped != value) {
			warnings.add(name + " = " + value + " is outside " + min + ".." + max + ", using " + clamped);
		}
		return clamped;
	}

	public long runningOutHorizonSeconds() {
		return runningOutHorizonMinutes * 60L;
	}
}
