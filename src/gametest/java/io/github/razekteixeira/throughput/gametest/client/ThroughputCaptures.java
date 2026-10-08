package io.github.razekteixeira.throughput.gametest.client;

import java.util.StringJoiner;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Builds a small smelting floor in a real client and captures the media used on the project page:
 * {@code hero}, {@code stats}, {@code alerts} and the {@code watch_NNN} frames of the action bar GIF.
 *
 * <p>Run with {@code ./gradlew runClientGameTest}; screenshots land in {@code build/run/clientGameTest/screenshots}.
 * Everything shown is real mod output from commands typed into chat, nothing is staged in an editor.
 */
public class ThroughputCaptures implements FabricClientGameTest {
	private static final int COLUMNS = 6;
	private static final int WATCH_FRAMES = 32;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		context.runOnClient(client -> client.options.guiScale().set(2));

		try (TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(settings -> {
					settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
					settings.setAllowCommands(true);
				})
				.create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
			BlockPos origin = feet.offset(0, 0, 8);

			buildFloor(server, origin);
			server.runCommand("flow factory create smelter");
			server.runCommand("flow addarea smelter %d %d %d %d %d %d".formatted(
					origin.getX() - 5, origin.getY(), origin.getZ(), origin.getX() + 5, origin.getY() + 4, origin.getZ()));
			server.runCommand("tick sprint 3000");
			server.waitFor(s -> !s.tickRateManager().isSprinting(), 20 * 60 * 5);

			server.runCommand("tp @p %.1f %d %.1f 0 0".formatted(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() - 6.5));
			context.waitTicks(10);
			context.getInput().lookAt(origin.above(2));
			world.getConnection().waitForChunksRender();

			clearChat(context);
			context.getInput().pressKey(options -> options.keyToggleGui);
			context.waitTicks(2);
			context.takeScreenshot("hero");
			context.getInput().pressKey(options -> options.keyToggleGui);

			// Spectators render no hand or hotbar, so the chat shots show only the factory and the report.
			server.runCommand("gamemode spectator @p");
			context.waitTicks(5);
			clearChat(context);

			typeCommand(context, world, "flow stats smelter 10m");
			context.takeScreenshot("stats");
			// Same reports through the server console, so the exact text lands in the log for the
			// project page's chat panel (which must match these screenshots).
			server.runCommand("flow stats smelter 10m");
			server.runCommand("flow alerts smelter");

			clearChat(context);
			typeCommand(context, world, "flow alerts smelter");
			context.takeScreenshot("alerts");

			clearChat(context);
			typeCommand(context, world, "flow watch smelter");
			clearChat(context);
			for (int frame = 0; frame < WATCH_FRAMES; frame++) {
				context.waitTicks(5);
				context.takeScreenshot("watch_%03d".formatted(frame));
			}
			typeCommand(context, world, "flow unwatch");
			clearChat(context);

			cinematic(context, world, server, origin, 12_000, origin.offset(-8, 2, -5), origin.offset(0, 2, 0), "gallery_angle");
			cinematic(context, world, server, origin, 18_000, origin.offset(6, 2, -5), origin.offset(-1, 2, 0), "gallery_night");
			cinematic(context, world, server, origin, 6_000, origin.offset(-3, 1, -3), origin.offset(-5, 2, 0), "gallery_closeup");
		}
	}

	/**
	 * Six furnace lines: input chest, hopper, furnace, hopper, output chest. Line 0 gets little ore so
	 * it runs dry, line 5's output chest is pre-filled so it jams, and coal is limited everywhere.
	 */
	private static void buildFloor(TestServerContext server, BlockPos o) {
		int x = o.getX();
		int y = o.getY();
		int z = o.getZ();
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("fill %d %d %d %d %d %d minecraft:polished_andesite".formatted(x - 9, y - 1, z - 9, x + 9, y - 1, z + 3));
		server.runCommand("fill %d %d %d %d %d %d minecraft:polished_deepslate".formatted(x - 9, y, z + 2, x + 9, y + 6, z + 2));
		server.runCommand("fill %d %d %d %d %d %d minecraft:waxed_cut_copper".formatted(x - 9, y + 6, z + 1, x + 9, y + 6, z + 1));
		for (int i = 0; i < COLUMNS; i++) {
			int cx = x - 5 + 2 * i;
			server.runCommand("setblock %d %d %d minecraft:chest[facing=north]%s".formatted(cx, y, z, i == COLUMNS - 1 ? jammedChest() : ""));
			server.runCommand("setblock %d %d %d minecraft:hopper[facing=down]".formatted(cx, y + 1, z));
			server.runCommand("setblock %d %d %d minecraft:furnace[facing=north]".formatted(cx, y + 2, z));
			server.runCommand("setblock %d %d %d minecraft:hopper[facing=down]".formatted(cx, y + 3, z));
			server.runCommand("setblock %d %d %d minecraft:chest[facing=north]".formatted(cx, y + 4, z));
			server.runCommand("setblock %d %d %d minecraft:lantern".formatted(cx, y + 5, z));
			int ore = i == 0 ? 6 : 64;
			server.runCommand("item replace block %d %d %d container.0 with minecraft:raw_iron %d".formatted(cx, y + 4, z, ore));
			if (i != 0) {
				server.runCommand("item replace block %d %d %d container.1 with minecraft:raw_iron 64".formatted(cx, y + 4, z));
			}
			server.runCommand("item replace block %d %d %d container.1 with minecraft:coal 3".formatted(cx, y + 2, z));
		}
	}

	/** A HUD-free shot from {@code eye} towards {@code target} at the given time of day. */
	private static void cinematic(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server,
			BlockPos origin, int timeOfDay, BlockPos eye, BlockPos target, String name) {
		server.runCommand("time set " + timeOfDay);
		server.runCommand("tp @p %.1f %d %.1f".formatted(eye.getX() + 0.5, eye.getY(), eye.getZ() + 0.5));
		context.waitTicks(10);
		context.getInput().lookAt(target);
		world.getConnection().waitForChunksRender();
		context.getInput().pressKey(options -> options.keyToggleGui);
		context.waitTicks(20);
		context.takeScreenshot(name);
		context.getInput().pressKey(options -> options.keyToggleGui);
	}

	private static String jammedChest() {
		StringJoiner items = new StringJoiner(",", "{Items:[", "]}");
		for (int slot = 0; slot < 27; slot++) {
			items.add("{Slot:%db,id:\"minecraft:cobblestone\",count:64}".formatted(slot));
		}
		return items.toString();
	}

	private static void typeCommand(ClientGameTestContext context, TestSingleplayerContext world, String command) {
		context.getInput().pressKey(options -> options.keyChat);
		context.getInput().typeChars("/" + command);
		context.getInput().holdKeyFor(InputConstants.KEY_RETURN, 0);
		world.getConnection().waitForServerboundPackets();
		world.getConnection().waitForClientboundPackets();
		context.waitTicks(2);
	}

	private static void clearChat(ClientGameTestContext context) {
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
	}
}
