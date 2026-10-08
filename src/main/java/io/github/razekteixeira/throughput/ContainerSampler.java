package io.github.razekteixeira.throughput;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;

import io.github.razekteixeira.throughput.core.ContainerReading;

/** Reads any block exposing Fabric item storage: vanilla containers and every mod that registers one. */
public final class ContainerSampler {
	private ContainerSampler() {
	}

	/** Storage at {@code pos}, or {@code null} when the chunk is unloaded or the block holds no items. */
	public static @Nullable Storage<ItemVariant> storageAt(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		// The sided lookup returns the combined inventory for either half of a double chest. Read each
		// half on its own so tracking both halves does not count the contents twice.
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof ChestBlockEntity chest) {
			return ContainerStorage.of(chest, null);
		}
		return ItemStorage.SIDED.find(level, pos, null);
	}

	public static @Nullable ContainerReading read(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof Container container && isVanilla(blockEntity.getType())) {
			return readContainer(container);
		}
		Storage<ItemVariant> storage = storageAt(level, pos);
		return storage == null ? null : readStorage(storage);
	}

	/**
	 * Fast path for vanilla block entities: reads slots directly instead of through Transfer API
	 * views. Modded block entities always take the Transfer API path, because a mod may expose a
	 * different inventory through it than its raw {@link Container} slots.
	 */
	static ContainerReading readContainer(Container container) {
		Map<String, Long> counts = new HashMap<>();
		boolean full = true;
		boolean empty = true;
		int size = container.getContainerSize();
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				full = false;
				continue;
			}
			empty = false;
			if (stack.getCount() < Math.min(container.getMaxStackSize(), stack.getMaxStackSize())) {
				full = false;
			}
			counts.merge(itemId(stack.getItem()), (long) stack.getCount(), Long::sum);
		}
		return new ContainerReading(counts, size > 0 && full, empty);
	}

	static ContainerReading readStorage(Storage<ItemVariant> storage) {
		Map<String, Long> counts = new HashMap<>();
		boolean full = true;
		boolean empty = true;
		boolean anySlot = false;
		for (StorageView<ItemVariant> view : storage) {
			anySlot = true;
			long amount = view.getAmount();
			if (view.isResourceBlank() || amount == 0) {
				full = false;
				continue;
			}
			empty = false;
			if (amount < view.getCapacity()) {
				full = false;
			}
			counts.merge(itemId(view.getResource().getItem()), amount, Long::sum);
		}
		return new ContainerReading(counts, anySlot && full, empty);
	}

	/** Registries are frozen once the server runs, so ids can be cached by identity. Server thread only. */
	private static final Map<Item, String> ITEM_IDS = new IdentityHashMap<>();
	private static final Map<BlockEntityType<?>, Boolean> VANILLA_TYPES = new IdentityHashMap<>();

	static String itemId(Item item) {
		return ITEM_IDS.computeIfAbsent(item, i -> BuiltInRegistries.ITEM.getKey(i).toString());
	}

	private static boolean isVanilla(BlockEntityType<?> type) {
		return VANILLA_TYPES.computeIfAbsent(type, t -> {
			Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(t);
			return id != null && Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace());
		});
	}

	/** The other half of a double chest, if {@code pos} is one. */
	public static @Nullable BlockPos otherChestHalf(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			return pos.relative(ChestBlock.getConnectedDirection(state));
		}
		return null;
	}
}
