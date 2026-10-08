package io.github.razekteixeira.throughput.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;

import io.github.razekteixeira.throughput.ContainerSampler;
import io.github.razekteixeira.throughput.ThroughputConfig;
import io.github.razekteixeira.throughput.ThroughputReports;
import io.github.razekteixeira.throughput.ThroughputSavedData;
import io.github.razekteixeira.throughput.ThroughputService;
import io.github.razekteixeira.throughput.core.Alert;
import io.github.razekteixeira.throughput.core.ContainerReading;
import io.github.razekteixeira.throughput.core.ContainerStatus;
import io.github.razekteixeira.throughput.core.ThroughputState;
import io.github.razekteixeira.throughput.core.TrackedFactory;
import io.github.razekteixeira.throughput.core.Window;

/** Runs inside a real dedicated server with real block entities, no client and no Minecraft account. */
public class ThroughputGameTests {
	private static final String IRON = "minecraft:iron_ingot";
	private static final String COBBLE = "minecraft:cobblestone";

	private static TrackedFactory newFactory(GameTestHelper helper, BlockPos... relative) {
		TrackedFactory factory = new TrackedFactory(helper.getLevel().dimension().identifier().toString());
		for (BlockPos pos : relative) {
			factory.add(helper.absolutePos(pos).asLong());
		}
		return factory;
	}

	private static Container container(GameTestHelper helper, BlockPos relative) {
		BlockEntity blockEntity = helper.getBlockEntity(relative, BlockEntity.class);
		if (blockEntity instanceof Container container) {
			return container;
		}
		throw new AssertionError("no container at " + relative);
	}

	// AC1: real insertions and removals in a real chest are measured per item.
	@GameTest
	public void chestInsertAndRemoveAreMeasured(GameTestHelper helper) {
		BlockPos chest = new BlockPos(1, 1, 1);
		helper.setBlock(chest, Blocks.CHEST);
		TrackedFactory factory = newFactory(helper, chest);
		MinecraftServer server = helper.getLevel().getServer();

		ThroughputService.sample(server, factory, 1);
		container(helper, chest).setItem(0, new ItemStack(Items.IRON_INGOT, 32));
		Map<String, Long> afterInsert = ThroughputService.sample(server, factory, 2);
		container(helper, chest).removeItem(0, 10);
		Map<String, Long> afterRemove = ThroughputService.sample(server, factory, 3);

		helper.assertValueEqual(afterInsert, Map.of(IRON, 32L), "delta after inserting 32 iron");
		helper.assertValueEqual(afterRemove, Map.of(IRON, -10L), "delta after removing 10 iron");
		long[] totals = factory.history().totals(Window.ONE_MINUTE, 3).get(IRON);
		helper.assertValueEqual(totals[0], 32L, "produced");
		helper.assertValueEqual(totals[1], 10L, "consumed");
		helper.succeed();
	}

	// AC2: a real hopper moving items between tracked containers produces zero net flow.
	@GameTest(maxTicks = 400)
	public void hopperTransferInsideFactoryNetsToZero(GameTestHelper helper) {
		BlockPos source = new BlockPos(1, 3, 1);
		BlockPos hopper = new BlockPos(1, 2, 1);
		BlockPos target = new BlockPos(1, 1, 1);
		helper.setBlock(target, Blocks.CHEST);
		helper.setBlock(hopper, Blocks.HOPPER);
		helper.setBlock(source, Blocks.CHEST);
		container(helper, source).setItem(0, new ItemStack(Items.COBBLESTONE, 10));

		TrackedFactory whole = newFactory(helper, source, hopper, target);
		TrackedFactory sourceOnly = newFactory(helper, source);
		MinecraftServer server = helper.getLevel().getServer();
		List<Map<String, Long>> nonZero = new ArrayList<>();
		long[] second = {0};
		helper.onEachTick(() -> {
			second[0]++;
			Map<String, Long> delta = ThroughputService.sample(server, whole, second[0]);
			if (!delta.isEmpty()) {
				nonZero.add(delta);
			}
			ThroughputService.sample(server, sourceOnly, second[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertValueEqual(container(helper, target).getItem(0).getCount(), 10, "cobblestone moved into the target chest");
			helper.assertTrue(nonZero.isEmpty(), "internal moves must net to zero, got " + nonZero);
			long consumed = sourceOnly.history().totals(Window.TEN_MINUTES, second[0]).get(COBBLE)[1];
			helper.assertValueEqual(consumed, 10L, "control: tracking only the source sees 10 consumed");
		});
	}

	// AC5: full, empty, missing and broken containers; vanilla furnace and barrel through the Transfer API.
	@GameTest
	public void containerStatesAndBrokenContainers(GameTestHelper helper) {
		BlockPos full = new BlockPos(1, 1, 1);
		BlockPos empty = new BlockPos(3, 1, 1);
		BlockPos furnace = new BlockPos(5, 1, 1);
		BlockPos barrel = new BlockPos(1, 1, 3);
		BlockPos stone = new BlockPos(3, 1, 3);
		helper.setBlock(full, Blocks.CHEST);
		helper.setBlock(empty, Blocks.CHEST);
		helper.setBlock(furnace, Blocks.FURNACE);
		helper.setBlock(barrel, Blocks.BARREL);
		helper.setBlock(stone, Blocks.STONE);
		Container fullChest = container(helper, full);
		for (int slot = 0; slot < fullChest.getContainerSize(); slot++) {
			fullChest.setItem(slot, new ItemStack(Items.STONE, 64));
		}
		container(helper, furnace).setItem(1, new ItemStack(Items.COAL, 5));
		container(helper, barrel).setItem(0, new ItemStack(Items.WHEAT, 3));

		ServerLevel level = helper.getLevel();
		ContainerReading fullReading = ContainerSampler.read(level, helper.absolutePos(full));
		ContainerReading emptyReading = ContainerSampler.read(level, helper.absolutePos(empty));
		ContainerReading furnaceReading = ContainerSampler.read(level, helper.absolutePos(furnace));
		ContainerReading barrelReading = ContainerSampler.read(level, helper.absolutePos(barrel));

		helper.assertValueEqual(fullReading.status(), ContainerStatus.FULL, "27 stacks of 64");
		helper.assertValueEqual(fullReading.counts(), Map.of("minecraft:stone", 27L * 64), "full chest counts");
		helper.assertValueEqual(emptyReading.status(), ContainerStatus.EMPTY, "empty chest");
		helper.assertValueEqual(furnaceReading.counts(), Map.of("minecraft:coal", 5L), "furnace fuel slot");
		helper.assertValueEqual(furnaceReading.status(), ContainerStatus.PARTIAL, "furnace");
		helper.assertValueEqual(barrelReading.counts(), Map.of("minecraft:wheat", 3L), "barrel");
		helper.assertTrue(ContainerSampler.read(level, helper.absolutePos(stone)) == null, "stone is not a container");

		TrackedFactory factory = newFactory(helper, full, empty);
		MinecraftServer server = helper.getLevel().getServer();
		ThroughputService.sample(server, factory, 1);
		helper.setBlock(full, Blocks.AIR); // broken: its 1728 stone must not count as consumed
		Map<String, Long> delta = ThroughputService.sample(server, factory, 2);
		helper.assertTrue(delta.isEmpty(), "breaking a container is not consumption, got " + delta);
		helper.assertValueEqual(factory.status(helper.absolutePos(full).asLong()), ContainerStatus.MISSING, "broken chest");
		helper.assertTrue(factory.alerts(2).stream().anyMatch(a -> a.kind() == Alert.Kind.MISSING), "missing alert");
		helper.succeed();
	}

	// Double chests: each half is read on its own, so tracking both halves never double counts.
	@GameTest
	public void doubleChestHalvesAreNotDoubleCounted(GameTestHelper helper) {
		BlockPos left = new BlockPos(1, 1, 1);
		BlockPos right = new BlockPos(2, 1, 1);
		helper.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		container(helper, left).setItem(0, new ItemStack(Items.DIAMOND, 5));
		ServerLevel level = helper.getLevel();

		// Precondition: the chests really are connected, otherwise this test proves nothing.
		Storage<ItemVariant> combined = ItemStorage.SIDED.find(level, helper.absolutePos(right), null);
		long combinedDiamonds = 0;
		for (StorageView<ItemVariant> view : combined) {
			if (view.getResource().isOf(Items.DIAMOND)) {
				combinedDiamonds += view.getAmount();
			}
		}
		helper.assertValueEqual(combinedDiamonds, 5L, "the sided lookup on the right half sees the left half's diamonds");

		helper.assertValueEqual(ContainerSampler.read(level, helper.absolutePos(left)).counts(), Map.of("minecraft:diamond", 5L), "left half");
		helper.assertValueEqual(ContainerSampler.read(level, helper.absolutePos(right)).counts(), Map.of(), "right half reads only itself");
		helper.assertValueEqual(ContainerSampler.otherChestHalf(level, helper.absolutePos(left)), helper.absolutePos(right), "partner of left");
		helper.succeed();
	}

	// AC7: commands work on a dedicated server, with permission checks and argument errors.
	@GameTest
	public void commandsRunOnDedicatedServer(GameTestHelper helper) throws CommandSyntaxException {
		BlockPos chest = new BlockPos(2, 1, 2);
		BlockPos stone = new BlockPos(4, 1, 2);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(stone, Blocks.STONE);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		CommandSourceStack op = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
		CommandSourceStack player = op.withPermission(LevelBasedPermissionSet.ALL);
		BlockPos c = helper.absolutePos(chest);
		BlockPos s = helper.absolutePos(stone);
		String name = "gt_cmd";
		ThroughputService.state(server).delete(name); // the test world persists between runs

		helper.assertValueEqual(dispatcher.execute("throughput factory create " + name, op), 1, "create");
		helper.assertValueEqual(dispatcher.execute("throughput add " + name + " " + c.getX() + " " + c.getY() + " " + c.getZ(), op), 1, "add chest");
		expectFailure(helper, dispatcher, "throughput add " + name + " " + s.getX() + " " + s.getY() + " " + s.getZ(), op, "adding stone");
		expectFailure(helper, dispatcher, "throughput factory create " + name, op, "duplicate name");
		expectFailure(helper, dispatcher, "throughput factory create Bad!Name", op, "invalid name");
		expectFailure(helper, dispatcher, "throughput stats " + name + " 2d", op, "unknown window");
		expectFailure(helper, dispatcher, "throughput factory create other", player, "non-op creating a factory");
		expectFailure(helper, dispatcher, "throughput stats nope", op, "unknown factory");

		dispatcher.execute("throughput stats " + name, player);
		dispatcher.execute("throughput stats " + name + " 1h", player);
		dispatcher.execute("throughput alerts " + name, player);
		// Other tests in the batch share the server, so compare with the live state instead of a constant.
		ThroughputState state = ThroughputService.state(server);
		helper.assertValueEqual(dispatcher.execute("throughput factory list", player), state.factories().size(), "list counts every factory");
		helper.assertTrue(state.factories().containsKey(name), "list includes the new factory");
		helper.assertValueEqual(state.get(name).orElseThrow().positions().size(), 1, "one container tracked");
		helper.assertValueEqual(dispatcher.execute("throughput factory delete " + name, op), 1, "delete");

		// Permission layer (fabric-permissions-api, falling back to configured vanilla levels).
		expectFailure(helper, dispatcher, "throughput reload", player, "level 0 player reloading settings");
		expectFailure(helper, dispatcher, "flow reload", player, "level 0 player reloading through the alias");
		helper.assertValueEqual(dispatcher.execute("flow reload", op), 1, "op reloads through the alias");
		CommandSourceStack moderator = op.withPermission(LevelBasedPermissionSet.MODERATOR);
		expectFailure(helper, dispatcher, "throughput factory create mod_made", moderator, "level 1 cannot manage (default manage = 2)");
		CommandSourceStack gamemaster = op.withPermission(LevelBasedPermissionSet.GAMEMASTER);
		helper.assertValueEqual(dispatcher.execute("throughput factory create gm_made", gamemaster), 1, "level 2 can manage");
		expectFailure(helper, dispatcher, "throughput reload", gamemaster, "level 2 cannot reload (default admin = 3)");
		state.delete("gm_made");
		helper.assertTrue(state.get(name).isEmpty(), "deleted");
		helper.succeed();
	}

	// Configured limits are enforced by the commands, and /flow reload applies a changed file.
	@GameTest
	public void configuredLimitsAreEnforced(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		CommandSourceStack op = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
		Path config = ThroughputConfig.path();
		String original = Files.exists(config) ? Files.readString(config) : null;
		String name = "gt_cap";
		ThroughputService.state(server).delete(name);
		for (int x = 1; x <= 5; x++) {
			helper.setBlock(new BlockPos(x, 1, 1), Blocks.BARREL);
		}
		BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos to = helper.absolutePos(new BlockPos(5, 1, 1));
		BlockPos fifth = helper.absolutePos(new BlockPos(5, 1, 1));
		// Everything below runs within one tick, so other tests never see the temporary limits.
		try {
			Files.writeString(config, "{\"maxContainersPerFactory\": 3, \"maxAreaBlocks\": 8}");
			dispatcher.execute("flow reload", op);
			helper.assertValueEqual(ThroughputConfig.get().maxContainersPerFactory(), 3, "reload applied the file");

			dispatcher.execute("flow factory create " + name, op);
			int added = dispatcher.execute("flow addarea %s %d %d %d %d %d %d".formatted(name,
					from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ()), op);
			helper.assertValueEqual(added, 3, "addarea stops at the configured limit");
			expectFailure(helper, dispatcher, "flow add %s %d %d %d".formatted(name, fifth.getX(), fifth.getY(), fifth.getZ()), op,
					"adding past maxContainersPerFactory");
			expectFailure(helper, dispatcher, "flow addarea %s %d %d %d %d %d %d".formatted(name,
					from.getX(), from.getY(), from.getZ(), to.getX(), to.getY() + 1, to.getZ()), op, "area of 10 blocks over maxAreaBlocks 8");
		} finally {
			if (original == null) {
				Files.deleteIfExists(config);
			} else {
				Files.writeString(config, original);
			}
			dispatcher.execute("flow reload", op);
			ThroughputService.state(server).delete(name);
		}
		helper.assertValueEqual(ThroughputConfig.get().maxContainersPerFactory(), 4096, "defaults restored");
		helper.succeed();
	}

	private static void expectFailure(GameTestHelper helper, CommandDispatcher<CommandSourceStack> dispatcher, String command,
			CommandSourceStack source, String what) {
		try {
			dispatcher.execute(command, source);
		} catch (CommandSyntaxException expected) {
			return;
		}
		helper.fail(what + " should have been rejected: /" + command);
	}

	/** Collects what commands print, which a suppressed source never renders. */
	private static final class CapturingSource implements CommandSource {
		final StringBuilder out = new StringBuilder();

		@Override
		public void sendSystemMessage(Component message) {
			out.append(message.getString()).append('\n');
		}

		@Override
		public boolean acceptsSuccess() {
			return true;
		}

		@Override
		public boolean acceptsFailure() {
			return true;
		}

		@Override
		public boolean shouldInformAdmins() {
			return false;
		}

		String take() {
			String text = out.toString();
			out.setLength(0);
			return text;
		}
	}

	// AC7 rendering: stats, alerts (with and without coordinates), ticker and watch produce real output.
	@GameTest(maxTicks = 400)
	public void reportsRenderForOpsAndPlayers(GameTestHelper helper) {
		// The scenario samples 15 seconds into the past, so wait until the fresh test world is that old.
		long wait = Math.max(0, (16 - ThroughputService.nowSecond(helper.getLevel().getServer())) * ThroughputService.TICKS_PER_SAMPLE);
		helper.runAfterDelay(wait, () -> {
			try {
				renderScenario(helper);
			} catch (CommandSyntaxException e) {
				helper.fail("command failed: " + e.getMessage());
			}
		});
	}

	private static void renderScenario(GameTestHelper helper) throws CommandSyntaxException {
		BlockPos output = new BlockPos(1, 1, 1);
		BlockPos jammed = new BlockPos(3, 1, 1);
		helper.setBlock(output, Blocks.CHEST);
		helper.setBlock(jammed, Blocks.CHEST);
		Container jammedChest = container(helper, jammed);
		for (int slot = 0; slot < jammedChest.getContainerSize(); slot++) {
			jammedChest.setItem(slot, new ItemStack(Items.STONE, 64));
		}
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		String name = "gt_render";
		ThroughputState state = ThroughputService.state(server);
		state.delete(name);
		TrackedFactory factory = state.create(name, level.dimension().identifier().toString()).orElseThrow();
		factory.add(helper.absolutePos(output).asLong());
		factory.add(helper.absolutePos(jammed).asLong());
		// Four buffers that held ore and then emptied: they must collapse into one grouped line.
		List<BlockPos> drained = List.of(new BlockPos(1, 1, 4), new BlockPos(3, 1, 4), new BlockPos(5, 1, 4), new BlockPos(7, 1, 4));
		for (BlockPos pos : drained) {
			helper.setBlock(pos, Blocks.BARREL);
			container(helper, pos).setItem(0, new ItemStack(Items.RAW_IRON, 8));
			factory.add(helper.absolutePos(pos).asLong());
		}

		long now = ThroughputService.nowSecond(server);
		ThroughputService.sample(server, factory, now - 15);
		drained.forEach(pos -> container(helper, pos).clearContent());
		ThroughputService.sample(server, factory, now - 14); // empty for 14 s by now, past the 10 s threshold
		container(helper, output).setItem(0, new ItemStack(Items.IRON_INGOT, 30));
		ThroughputService.sample(server, factory, now - 1);
		ThroughputService.sample(server, factory, now);

		CapturingSource capture = new CapturingSource();
		CommandSourceStack op = server.createCommandSourceStack().withLevel(level).withSource(capture);
		CommandSourceStack player = op.withPermission(LevelBasedPermissionSet.ALL);
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		BlockPos j = helper.absolutePos(jammed);
		String coords = j.getX() + " " + j.getY() + " " + j.getZ();

		dispatcher.execute("flow stats " + name + " 1m", player);
		String stats = capture.take();
		helper.assertTrue(stats.contains("+120.0 Iron Ingot"), "30 ingots in 15 sampled seconds is 120/min:\n" + stats);
		helper.assertTrue(stats.contains("sampled 15s"), "header shows sampled time:\n" + stats);
		String ironLine = stats.lines().filter(l -> l.contains("Iron Ingot")).findFirst().orElseThrow();
		String spark = ironLine.substring(ironLine.indexOf("Iron Ingot ") + "Iron Ingot ".length());
		helper.assertValueEqual(spark.length(), 15, "sparkline covers only the 15 sampled one-second buckets: '" + spark + "'");

		dispatcher.execute("flow alerts " + name, op);
		String opAlerts = capture.take();
		helper.assertTrue(opAlerts.contains("BLOCKED 1 container full") && opAlerts.contains(coords), "ops see where the jam is:\n" + opAlerts);
		helper.assertTrue(opAlerts.contains("RAN DRY 4 containers empty") && opAlerts.contains("(+1 more)"),
				"four drained barrels are one grouped line listing three positions:\n" + opAlerts);
		helper.assertValueEqual(opAlerts.lines().filter(l -> l.contains("RAN DRY")).count(), 1L, "one RAN DRY line");

		dispatcher.execute("flow alerts " + name + " all", op);
		String allAlerts = capture.take();
		helper.assertFalse(allAlerts.contains("more)"), "'all' lists every container:\n" + allAlerts);
		for (BlockPos pos : drained) {
			BlockPos a = helper.absolutePos(pos);
			helper.assertTrue(allAlerts.contains(a.getX() + " " + a.getY() + " " + a.getZ()), "'all' includes " + a + ":\n" + allAlerts);
		}

		dispatcher.execute("flow alerts " + name, player);
		String playerAlerts = capture.take();
		helper.assertTrue(playerAlerts.contains("BLOCKED 1 container full"), "players see the alert:\n" + playerAlerts);
		helper.assertFalse(playerAlerts.contains(coords), "but not the coordinates:\n" + playerAlerts);

		String ticker = ThroughputReports.ticker(name, factory, now).getString();
		helper.assertTrue(ticker.contains("Iron Ingot") && ticker.contains("5 alerts"), "ticker: " + ticker);

		ServerPlayer mock = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		CommandSourceStack mockSource = mock.createCommandSourceStack().withSource(capture);
		helper.assertValueEqual(dispatcher.execute("flow watch " + name, mockSource), 1, "watch");
		helper.assertValueEqual(dispatcher.execute("flow unwatch", mockSource), 1, "unwatch");
		helper.assertValueEqual(dispatcher.execute("flow unwatch", mockSource), 0, "unwatch twice");

		state.delete(name);
		helper.succeed();
	}

	// End to end: the real server tick samples a registered factory and the result persists through the codec.
	@GameTest(maxTicks = 120)
	public void serverTickSamplesRegisteredFactories(GameTestHelper helper) {
		BlockPos chest = new BlockPos(1, 1, 1);
		helper.setBlock(chest, Blocks.CHEST);
		MinecraftServer server = helper.getLevel().getServer();
		ThroughputSavedData data = ThroughputSavedData.get(server);
		String name = "gt_tick";
		data.state().delete(name);
		TrackedFactory factory = data.state().create(name, helper.getLevel().dimension().identifier().toString()).orElseThrow();
		factory.add(helper.absolutePos(chest).asLong());

		Container container = container(helper, chest);
		helper.onEachTick(() -> {
			ItemStack stack = container.getItem(0);
			if (stack.isEmpty()) {
				container.setItem(0, new ItemStack(Items.IRON_INGOT, 1));
			} else if (stack.getCount() < 64) {
				stack.grow(1);
				container.setChanged();
			}
		});
		helper.runAfterDelay(90, () -> {
			long now = ThroughputService.nowSecond(server);
			long[] totals = factory.history().totals(Window.ONE_MINUTE, now).get(IRON);
			helper.assertTrue(totals != null && totals[0] >= 40, "the tick hook recorded iron production, got "
					+ (totals == null ? "nothing" : totals[0]));
			long covered = factory.history().coveredSeconds(Window.ONE_MINUTE, now);
			helper.assertTrue(covered >= 3, "at least 3 sampled seconds, got " + covered);

			// The unreadable-file guard looks for the file where Minecraft writes it; prove the path matches.
			server.getDataStorage().saveAndJoin();
			helper.assertTrue(java.nio.file.Files.exists(ThroughputSavedData.dataFile(server)),
					"saved data is at " + ThroughputSavedData.dataFile(server));
			Tag saved = ThroughputSavedData.TYPE.codec().encodeStart(NbtOps.INSTANCE, data).getOrThrow();
			ThroughputSavedData loaded = ThroughputSavedData.TYPE.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
			long[] reloaded = loaded.state().get(name).orElseThrow().history().totals(Window.ONE_MINUTE, now).get(IRON);
			helper.assertValueEqual(reloaded[0], totals[0], "production survives an NBT round trip");
			data.state().delete(name);
			helper.succeed();
		});
	}
}
