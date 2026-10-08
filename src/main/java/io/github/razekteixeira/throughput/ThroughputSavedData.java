package io.github.razekteixeira.throughput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

import io.github.razekteixeira.throughput.core.ThroughputState;
import io.github.razekteixeira.throughput.core.VersionedState;

/** Server-wide persistence of every factory definition and its history. */
public final class ThroughputSavedData extends SavedData {
	private static final Codec<ThroughputSavedData> CODEC = VersionedState.CODEC.xmap(ThroughputSavedData::new, ThroughputSavedData::versioned);
	private static final Identifier ID = Identifier.fromNamespaceAndPath(Throughput.MOD_ID, "factories");

	public static final SavedDataType<ThroughputSavedData> TYPE = new SavedDataType<>(ID, ThroughputSavedData::new, CODEC, null);

	private final VersionedState versioned;

	public ThroughputSavedData() {
		this(VersionedState.of(new ThroughputState()));
	}

	private ThroughputSavedData(VersionedState versioned) {
		this.versioned = versioned;
		versioned.problem().ifPresent(problem -> Throughput.LOGGER.error(
				"Throughput saved data is kept unchanged and read-only ({}). Factories are paused until this is fixed.", problem));
	}

	private VersionedState versioned() {
		return versioned;
	}

	public ThroughputState state() {
		return versioned.state();
	}

	public boolean isReadOnly() {
		return versioned.isReadOnly();
	}

	public Optional<String> problem() {
		return versioned.problem();
	}

	public static ThroughputSavedData get(MinecraftServer server) {
		SavedDataStorage storage = server.getDataStorage();
		ThroughputSavedData loaded = storage.get(TYPE);
		if (loaded != null) {
			return loaded;
		}
		// get() returns null both for "no file yet" and for "file Minecraft could not read" (for
		// example truncated by a crash mid-save). In the second case computeIfAbsent would start
		// empty and the next autosave would overwrite every factory, so lock the data instead.
		Path file = dataFile(server);
		if (Files.exists(file)) {
			ThroughputSavedData locked = new ThroughputSavedData(VersionedState.unreadable(backUp(file)));
			storage.set(TYPE, locked);
			locked.setDirty(false);
			return locked;
		}
		return storage.computeIfAbsent(TYPE);
	}

	/** Mirrors where SavedDataStorage keeps this type: {@code <world>/data/throughput/factories.dat}. */
	public static Path dataFile(MinecraftServer server) {
		return ID.withSuffix(".dat").resolveAgainst(server.getWorldPath(LevelResource.ROOT).resolve("data"));
	}

	private static String backUp(Path file) {
		Path copy = file.resolveSibling(file.getFileName() + ".unreadable-"
				+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		try {
			Files.copy(file, copy);
			return "could not read " + file.getFileName() + ", a copy was saved as " + copy.getFileName()
					+ "; restore a backup or delete the file to start fresh";
		} catch (IOException e) {
			return "could not read " + file.getFileName() + " and could not copy it (" + e.getMessage() + "); the file is left untouched";
		}
	}
}
