package io.github.razekteixeira.throughput;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import io.github.razekteixeira.throughput.core.Alert;
import io.github.razekteixeira.throughput.core.FlowHistory;
import io.github.razekteixeira.throughput.core.Format;
import io.github.razekteixeira.throughput.core.Settings;
import io.github.razekteixeira.throughput.core.TrackedFactory;
import io.github.razekteixeira.throughput.core.TrackedFactory.Rate;
import io.github.razekteixeira.throughput.core.Window;

/** Chat and action bar rendering. Plain vanilla components, so unmodded clients see everything. */
public final class ThroughputReports {
	static final int TOP_ITEMS = 8;
	private static final int SPARK_WIDTH = 20;

	private ThroughputReports() {
	}

	public static List<Rate> top(TrackedFactory factory, Window window, long now, boolean produced) {
		return factory.topRates(window, now, produced, TOP_ITEMS);
	}

	/** The item's series limited to buckets inside the sampled period, so time before tracking does not read as zero output. */
	static long[] sampledSeries(TrackedFactory factory, String item, Window window, long now, FlowHistory.Flow flow) {
		long[] series = factory.history().series(item, window, now, flow);
		long covered = factory.history().coveredSeconds(window, now);
		int live = (int) Math.min(series.length, Math.max(1, (covered + window.bucketSeconds() - 1) / window.bucketSeconds()));
		return Arrays.copyOfRange(series, series.length - live, series.length);
	}

	public static Component stats(String name, TrackedFactory factory, Window window, long now) {
		long covered = factory.history().coveredSeconds(window, now);
		MutableComponent text = Component.literal("Factory ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
				.append(Component.literal(" | last " + window.label() + " (sampled " + Format.duration(covered) + ") | "
						+ factory.positions().size() + " containers").withStyle(ChatFormatting.GRAY));
		if (covered == 0) {
			return text.append(Component.literal("\nNo samples yet, check again in a few seconds.").withStyle(ChatFormatting.GRAY));
		}
		appendSection(text, "Produced", top(factory, window, now, true), factory, window, now, true);
		appendSection(text, "Consumed", top(factory, window, now, false), factory, window, now, false);
		return text;
	}

	private static void appendSection(MutableComponent text, String title, List<Rate> rows, TrackedFactory factory,
			Window window, long now, boolean produced) {
		ChatFormatting colour = produced ? ChatFormatting.GREEN : ChatFormatting.RED;
		text.append(Component.literal("\n" + title + " per minute").withStyle(colour, ChatFormatting.UNDERLINE));
		if (rows.isEmpty()) {
			text.append(Component.literal("\n  nothing").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		FlowHistory.Flow flow = produced ? FlowHistory.Flow.PRODUCED : FlowHistory.Flow.CONSUMED;
		for (Rate row : rows) {
			String spark = Format.sparkline(sampledSeries(factory, row.item(), window, now, flow), SPARK_WIDTH);
			text.append(Component.literal("\n  " + (produced ? "+" : "-") + Format.amount(row.perMinute()) + " ").withStyle(colour))
					.append(itemName(row.item()).copy().withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" " + spark).withStyle(ChatFormatting.DARK_AQUA));
		}
	}

	/** @param showPositions false hides coordinates from players who cannot manage factories */
	/** Positions listed per alert kind in the grouped view; {@code /flow alerts <factory> all} lists every one. */
	static final int POSITIONS_PER_KIND = 3;

	/**
	 * Container alerts are grouped per kind (count, longest duration, a few positions), because a
	 * hopper chain easily produces a dozen identical RAN DRY lines that would push BLOCKED out of chat.
	 *
	 * @param showPositions false hides coordinates from players without {@code throughput.coordinates}
	 * @param everything    list every container instead of the first few per kind
	 */
	public static Component alerts(String name, TrackedFactory factory, long now, boolean showPositions, boolean everything) {
		List<Alert> alerts = alertsFor(factory, now);
		MutableComponent text = Component.literal("Alerts for ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
		if (alerts.isEmpty()) {
			return text.append(Component.literal("\n  All clear.").withStyle(ChatFormatting.GREEN));
		}
		text.append(Component.literal(" (" + alerts.size() + ")").withStyle(ChatFormatting.GRAY));
		for (Alert.Kind kind : Alert.Kind.values()) {
			List<Alert> ofKind = alerts.stream().filter(a -> a.kind() == kind).toList();
			if (ofKind.isEmpty()) {
				continue;
			}
			if (kind == Alert.Kind.RUNNING_OUT) {
				ofKind.forEach(alert -> text.append(Component.literal("\n  RUNNING OUT ").withStyle(ChatFormatting.LIGHT_PURPLE))
						.append(itemName(alert.item()).copy().withStyle(ChatFormatting.WHITE))
						.append(Component.literal(" empty in about " + Format.duration(alert.seconds())).withStyle(ChatFormatting.GRAY)));
			} else {
				text.append(Component.literal("\n  ")).append(containerGroup(kind, ofKind, showPositions, everything));
			}
		}
		return text;
	}

	private static Component containerGroup(Alert.Kind kind, List<Alert> alerts, boolean showPositions, boolean everything) {
		List<Alert> longestFirst = alerts.stream().sorted(Comparator.comparingLong(Alert::seconds).reversed()).toList();
		String count = alerts.size() + (alerts.size() == 1 ? " container " : " containers ");
		String longest = Format.duration(longestFirst.getFirst().seconds());
		MutableComponent line = switch (kind) {
			case BLOCKED -> Component.literal("BLOCKED ").withStyle(ChatFormatting.RED)
					.append(Component.literal(count + "full" + (alerts.size() == 1 ? " for " : ", longest ") + longest
							+ (alerts.size() == 1 ? " (whatever feeds it backs up)" : " (whatever feeds them backs up)")).withStyle(ChatFormatting.GRAY));
			case RAN_DRY -> Component.literal("RAN DRY ").withStyle(ChatFormatting.YELLOW)
					.append(Component.literal(count + "empty" + (alerts.size() == 1 ? " for " : ", longest ") + longest
							+ (alerts.size() == 1 ? " (what it feeds stalls once buffers drain)" : " (what they feed stalls once buffers drain)"))
							.withStyle(ChatFormatting.GRAY));
			case MISSING -> Component.literal("MISSING ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(count + "unloaded or no longer a container").withStyle(ChatFormatting.GRAY));
			case RUNNING_OUT -> throw new IllegalArgumentException("item alerts are not container alerts");
		};
		if (showPositions) {
			int shown = everything ? longestFirst.size() : Math.min(POSITIONS_PER_KIND, longestFirst.size());
			StringBuilder where = new StringBuilder();
			for (int i = 0; i < shown; i++) {
				where.append(i == 0 ? ": " : ", ").append(pos(longestFirst.get(i).pos()));
			}
			if (shown < longestFirst.size()) {
				where.append(" (+").append(longestFirst.size() - shown).append(" more)");
			}
			line.append(Component.literal(where.toString()).withStyle(ChatFormatting.WHITE));
		}
		return line;
	}

	/** Compact action bar line: top output, top input and alert count. */
	public static Component ticker(String name, TrackedFactory factory, long now) {
		MutableComponent text = Component.literal(name + " ").withStyle(ChatFormatting.GOLD);
		List<Rate> produced = top(factory, Window.ONE_MINUTE, now, true);
		List<Rate> consumed = top(factory, Window.ONE_MINUTE, now, false);
		if (!produced.isEmpty()) {
			text.append(Component.literal("+" + Format.amount(produced.getFirst().perMinute()) + "/m ").withStyle(ChatFormatting.GREEN))
					.append(itemName(produced.getFirst().item()).copy().withStyle(ChatFormatting.WHITE));
		}
		if (!consumed.isEmpty()) {
			text.append(Component.literal("  -" + Format.amount(consumed.getFirst().perMinute()) + "/m ").withStyle(ChatFormatting.RED))
					.append(itemName(consumed.getFirst().item()).copy().withStyle(ChatFormatting.WHITE));
		}
		if (produced.isEmpty() && consumed.isEmpty()) {
			text.append(Component.literal("idle").withStyle(ChatFormatting.GRAY));
		}
		int alerts = alertsFor(factory, now).size();
		if (alerts > 0) {
			text.append(Component.literal("  " + alerts + " alert" + (alerts == 1 ? "" : "s")).withStyle(ChatFormatting.RED));
		}
		return text;
	}

	/** Alerts with the server's configured thresholds. */
	public static List<Alert> alertsFor(TrackedFactory factory, long now) {
		Settings settings = ThroughputConfig.get();
		return factory.alerts(now, settings.alertAfterSeconds(), settings.runningOutHorizonSeconds());
	}

	static Component itemName(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
			return Component.literal(id);
		}
		return new ItemStack(BuiltInRegistries.ITEM.getValue(identifier)).getHoverName();
	}

	static String pos(Long packed) {
		if (packed == null) {
			return "?";
		}
		BlockPos pos = BlockPos.of(packed);
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}
}
