package io.github.razekteixeira.throughput;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Map;
import java.util.Optional;

import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import me.lucko.fabric.api.permissions.v0.Permissions;

import io.github.razekteixeira.throughput.core.ThroughputState;
import io.github.razekteixeira.throughput.core.TrackedFactory;
import io.github.razekteixeira.throughput.core.Window;

/** {@code /throughput}, alias {@code /flow}: reading is open to everyone, changing factories needs permission level 2. */
public final class ThroughputCommands {
	private static final DynamicCommandExceptionType UNKNOWN_FACTORY = new DynamicCommandExceptionType(
			name -> Component.literal("No factory named '" + name + "'. See /flow factory list"));
	private static final DynamicCommandExceptionType CANNOT_CREATE = new DynamicCommandExceptionType(
			name -> Component.literal("Cannot create '" + name + "': name taken or not 1-32 chars of a-z 0-9 _ -"));
	private static final SimpleCommandExceptionType NOT_A_CONTAINER = new SimpleCommandExceptionType(
			Component.literal("That block does not hold items"));
	private static final SimpleCommandExceptionType NO_TARGET = new SimpleCommandExceptionType(
			Component.literal("Look at a container or give a position"));
	private static final SimpleCommandExceptionType WRONG_DIMENSION = new SimpleCommandExceptionType(
			Component.literal("That factory lives in another dimension"));
	private static final DynamicCommandExceptionType AREA_TOO_LARGE = new DynamicCommandExceptionType(
			size -> Component.literal("Area has " + size + " blocks, the limit is " + ThroughputConfig.get().maxAreaBlocks()));
	private static final DynamicCommandExceptionType READ_ONLY = new DynamicCommandExceptionType(problem -> Component.literal(
			"Throughput data on this server is read-only: " + problem + ". See the server log."));
	private static final DynamicCommandExceptionType FACTORY_FULL = new DynamicCommandExceptionType(
			limit -> Component.literal("This factory already tracks the limit of " + limit + " containers (maxContainersPerFactory)"));
	private static final DynamicCommandExceptionType UNKNOWN_WINDOW = new DynamicCommandExceptionType(
			label -> Component.literal("Unknown window '" + label + "', use 1m, 10m, 1h or 10h"));

	private static final SuggestionProvider<CommandSourceStack> FACTORIES = (context, builder) ->
			SharedSuggestionProvider.suggest(ThroughputService.state(context.getSource().getServer()).factories().keySet(), builder);
	private static final SuggestionProvider<CommandSourceStack> WINDOWS = (context, builder) ->
			SharedSuggestionProvider.suggest(java.util.Arrays.stream(Window.values()).map(Window::label), builder);

	private ThroughputCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		Predicate<CommandSourceStack> manage = ThroughputCommands::canManage;
		var root = dispatcher.register(literal("throughput").requires(ThroughputCommands::canUse)
				.then(literal("factory")
						.then(literal("create").requires(manage)
								.then(argument("name", StringArgumentType.word()).executes(ThroughputCommands::create)))
						.then(literal("delete").requires(manage)
								.then(factoryArg().executes(ThroughputCommands::delete)))
						.then(literal("list").executes(ThroughputCommands::list)))
				.then(literal("add").requires(manage)
						.then(factoryArg()
								.executes(c -> add(c, lookedAt(c)))
								.then(argument("pos", BlockPosArgument.blockPos())
										.executes(c -> add(c, BlockPosArgument.getLoadedBlockPos(c, "pos"))))))
				.then(literal("addarea").requires(manage)
						.then(factoryArg()
								.then(argument("from", BlockPosArgument.blockPos())
										.then(argument("to", BlockPosArgument.blockPos()).executes(ThroughputCommands::addArea)))))
				.then(literal("remove").requires(manage)
						.then(factoryArg()
								.executes(c -> remove(c, lookedAt(c)))
								.then(argument("pos", BlockPosArgument.blockPos())
										.executes(c -> remove(c, BlockPosArgument.getLoadedBlockPos(c, "pos"))))))
				.then(literal("stats")
						.then(factoryArg()
								.executes(c -> stats(c, Window.TEN_MINUTES))
								.then(argument("window", StringArgumentType.word()).suggests(WINDOWS)
										.executes(c -> stats(c, window(c))))))
				.then(literal("alerts")
						.then(factoryArg()
								.executes(c -> alerts(c, false))
								.then(literal("all").executes(c -> alerts(c, true)))))
				.then(literal("watch")
						.then(factoryArg().executes(ThroughputCommands::watch)))
				.then(literal("unwatch").executes(ThroughputCommands::unwatch))
				.then(literal("reload").requires(ThroughputCommands::canAdmin).executes(ThroughputCommands::reload)));
		dispatcher.register(literal("flow").requires(ThroughputCommands::canUse).redirect(root));
	}

	// Permission nodes win when a permissions mod is installed; otherwise the configured vanilla levels apply.
	// Levels are read on every check so /throughput reload takes effect immediately.

	private static PermissionLevel level(int configured) {
		return PermissionLevel.byId(configured);
	}

	static boolean canUse(CommandSourceStack source) {
		return Permissions.check(source, "throughput.use", level(ThroughputConfig.get().permissionLevels().use()));
	}

	static boolean canManage(CommandSourceStack source) {
		return Permissions.check(source, "throughput.manage", level(ThroughputConfig.get().permissionLevels().manage()));
	}

	static boolean canSeeCoordinates(CommandSourceStack source) {
		return Permissions.check(source, "throughput.coordinates", level(ThroughputConfig.get().permissionLevels().coordinates()));
	}

	static boolean canAdmin(CommandSourceStack source) {
		return Permissions.check(source, "throughput.admin", level(ThroughputConfig.get().permissionLevels().admin()));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> factoryArg() {
		return argument("factory", StringArgumentType.word()).suggests(FACTORIES);
	}

	/** Every command goes through here, so a read-only state is explained instead of looking empty. */
	private static ThroughputState state(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ThroughputSavedData data = ThroughputSavedData.get(c.getSource().getServer());
		if (data.isReadOnly()) {
			throw READ_ONLY.create(data.problem().orElse("unknown problem"));
		}
		return data.state();
	}

	/** Every command that changes factories calls this first. */
	private static ThroughputState writableState(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return state(c);
	}

	private static void changed(CommandContext<CommandSourceStack> c) {
		ThroughputSavedData.get(c.getSource().getServer()).setDirty();
	}

	private static Map.Entry<String, TrackedFactory> factory(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String name = StringArgumentType.getString(c, "factory");
		TrackedFactory factory = state(c).get(name).orElseThrow(() -> UNKNOWN_FACTORY.create(name));
		return Map.entry(name, factory);
	}

	private static Window window(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String label = StringArgumentType.getString(c, "window");
		return Window.byLabel(label).orElseThrow(() -> UNKNOWN_WINDOW.create(label));
	}

	private static BlockPos lookedAt(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayer();
		if (player == null) {
			throw NO_TARGET.create();
		}
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			return blockHit.getBlockPos();
		}
		throw NO_TARGET.create();
	}

	private static String dimensionOf(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	private static int create(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String name = StringArgumentType.getString(c, "name");
		Optional<TrackedFactory> created = writableState(c).create(name, dimensionOf(c.getSource().getLevel()));
		if (created.isEmpty()) {
			throw CANNOT_CREATE.create(name);
		}
		changed(c);
		c.getSource().sendSuccess(() -> Component.literal("Created factory '" + name + "'. Add containers with /flow add "
				+ name + " or /flow addarea " + name + " <from> <to>").withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int delete(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String name = factory(c).getKey();
		writableState(c).delete(name);
		changed(c);
		c.getSource().sendSuccess(() -> Component.literal("Deleted factory '" + name + "'"), true);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Map<String, TrackedFactory> factories = state(c).factories();
		if (factories.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.literal("No factories yet. Create one with /flow factory create <name>"), false);
			return 0;
		}
		StringBuilder text = new StringBuilder("Factories:");
		factories.forEach((name, f) -> {
			text.append("\n  ").append(name).append(" (").append(f.positions().size())
					.append(" containers, ").append(f.dimension());
			if (f.lastSampleNanos() >= 0) {
				text.append(String.format(java.util.Locale.ROOT, ", last sample %.2f ms", f.lastSampleNanos() / 1_000_000.0));
			}
			text.append(")");
		});
		c.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
		return factories.size();
	}

	private static TrackedFactory factoryInThisDimension(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		writableState(c);
		TrackedFactory factory = factory(c).getValue();
		if (!factory.dimension().equals(dimensionOf(c.getSource().getLevel()))) {
			throw WRONG_DIMENSION.create();
		}
		return factory;
	}

	private static int add(CommandContext<CommandSourceStack> c, BlockPos pos) throws CommandSyntaxException {
		TrackedFactory factory = factoryInThisDimension(c);
		ServerLevel level = c.getSource().getLevel();
		if (ContainerSampler.storageAt(level, pos) == null) {
			throw NOT_A_CONTAINER.create();
		}
		if (factory.positions().size() >= ThroughputConfig.get().maxContainersPerFactory()) {
			throw FACTORY_FULL.create(ThroughputConfig.get().maxContainersPerFactory());
		}
		int added = addWithChestPartner(factory, level, pos);
		changed(c);
		int total = added;
		c.getSource().sendSuccess(() -> Component.literal("Added " + total + " container" + (total == 1 ? "" : "s")
				+ ", factory now has " + factory.positions().size()), true);
		return added;
	}

	private static int addArea(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		TrackedFactory factory = factoryInThisDimension(c);
		BlockPos from = BlockPosArgument.getLoadedBlockPos(c, "from");
		BlockPos to = BlockPosArgument.getLoadedBlockPos(c, "to");
		long volume = (long) (Math.abs(from.getX() - to.getX()) + 1) * (Math.abs(from.getY() - to.getY()) + 1)
				* (Math.abs(from.getZ() - to.getZ()) + 1);
		if (volume > ThroughputConfig.get().maxAreaBlocks()) {
			throw AREA_TOO_LARGE.create(volume);
		}
		ServerLevel level = c.getSource().getLevel();
		int added = 0;
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			if (ContainerSampler.storageAt(level, pos) != null) {
				added += addWithChestPartner(factory, level, pos.immutable());
			}
		}
		changed(c);
		int total = added;
		int limit = ThroughputConfig.get().maxContainersPerFactory();
		String cap = factory.positions().size() >= limit ? " (limit of " + limit + " reached)" : "";
		c.getSource().sendSuccess(() -> Component.literal("Added " + total + " containers from the area, factory now has "
				+ factory.positions().size() + cap), true);
		return added;
	}

	/** Double chests are one inventory to the player, so both halves always join and leave together. */
	private static int addWithChestPartner(TrackedFactory factory, ServerLevel level, BlockPos pos) {
		int limit = ThroughputConfig.get().maxContainersPerFactory();
		int added = 0;
		if (factory.positions().size() < limit && factory.add(pos.asLong())) {
			added++;
		}
		BlockPos otherHalf = ContainerSampler.otherChestHalf(level, pos);
		if (otherHalf != null && factory.positions().size() < limit && factory.add(otherHalf.asLong())) {
			added++;
		}
		return added;
	}

	private static int reload(CommandContext<CommandSourceStack> c) {
		String status = ThroughputConfig.load();
		// Permission levels may have changed: resend command trees so tab completion matches.
		MinecraftServer server = c.getSource().getServer();
		server.getPlayerList().getPlayers().forEach(player -> server.getCommands().sendCommands(player));
		c.getSource().sendSuccess(() -> Component.literal(status), true);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> c, BlockPos pos) throws CommandSyntaxException {
		TrackedFactory factory = factoryInThisDimension(c);
		int removed = factory.remove(pos.asLong()) ? 1 : 0;
		BlockPos otherHalf = ContainerSampler.otherChestHalf(c.getSource().getLevel(), pos);
		if (otherHalf != null && factory.remove(otherHalf.asLong())) {
			removed++;
		}
		changed(c);
		int total = removed;
		c.getSource().sendSuccess(() -> Component.literal(total == 0 ? "That position was not tracked"
				: "Removed " + total + " container" + (total == 1 ? "" : "s")), true);
		return removed;
	}

	private static int stats(CommandContext<CommandSourceStack> c, Window window) throws CommandSyntaxException {
		var entry = factory(c);
		long now = ThroughputService.nowSecond(c.getSource().getServer());
		c.getSource().sendSuccess(() -> ThroughputReports.stats(entry.getKey(), entry.getValue(), window, now), false);
		return ThroughputReports.top(entry.getValue(), window, now, true).size();
	}

	private static int alerts(CommandContext<CommandSourceStack> c, boolean everything) throws CommandSyntaxException {
		var entry = factory(c);
		long now = ThroughputService.nowSecond(c.getSource().getServer());
		boolean showPositions = canSeeCoordinates(c.getSource());
		c.getSource().sendSuccess(() -> ThroughputReports.alerts(entry.getKey(), entry.getValue(), now, showPositions, everything), false);
		return ThroughputReports.alertsFor(entry.getValue(), now).size();
	}

	private static int watch(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String name = factory(c).getKey();
		ThroughputService.watch(c.getSource().getPlayerOrException().getUUID(), name);
		c.getSource().sendSuccess(() -> Component.literal("Watching '" + name + "' on your action bar. /flow unwatch to stop"), false);
		return 1;
	}

	private static int unwatch(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		boolean was = ThroughputService.unwatch(c.getSource().getPlayerOrException().getUUID());
		c.getSource().sendSuccess(() -> Component.literal(was ? "Stopped watching" : "You were not watching a factory"), false);
		return was ? 1 : 0;
	}
}
