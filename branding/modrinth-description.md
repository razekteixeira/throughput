![Throughput: a tracked smelter with its stats in chat](https://razekteixeira.github.io/throughput/media/hero.png)

# Throughput

**Factorio-style production statistics for any Minecraft factory.**

You built the smelter array. Is it keeping up? Which input runs out first? Which output chest is backing everything up? Throughput answers with numbers instead of guesses: group a factory's containers once, then ask for per-item rates, history and alerts right in chat.

Server-side only. Players join with a vanilla client.

## Features

- **Per-item rates** produced and consumed per minute, over the last **1m, 10m, 1h or 10h**.
- **Sparkline history** next to every item, rendered in chat.
- **Bottleneck alerts**: outputs that are full and backing up, inputs that ran dry, containers that went missing, and inputs about to run out with a **time-to-empty estimate**.
- **Live ticker** on your action bar while you build or tune a line.
- **Any container**: chests, barrels, furnaces, hoppers, and modded storage that exposes Fabric item storage (most tech mods do).
- **Accurate by design**: moves between containers of the same factory cancel out, unloaded or broken containers never count as consumption, double chests never double count.
- **Survives restarts**: history follows game time and is saved with the world.
- **Safe on public servers**: anyone can read stats, only operators change factories, and coordinates in alerts are only shown to people allowed to see them.

## What it looks like

Real output from the six-furnace floor in the screenshots below (one line jammed on purpose, one short on ore):

```
Factory smelter | last 10m (sampled 2m 30s) | 30 containers
Produced per minute
  +30.4 Iron Ingot ██████▇▇▇▇▇▇▇▇▁
Consumed per minute
  -30.4 Raw Iron ██████▇▇▇▇▇▇▇▇▁
  -4.4 Coal ▁▁▁▁▁▁▁█▁▁▁▁▁▁▁
Alerts for smelter (9)
  BLOCKED 1 container full for 2m 31s (whatever feeds it backs up): 5 -60 8
  RAN DRY 7 containers empty, longest 2m 29s (what they feed stalls once buffers drain): -5 -56 8, -5 -57 8, -3 -56 8 (+4 more)
  RUNNING OUT Raw Iron empty in about 22m 48s
```

In game it is colour coded: green for produced, red for consumed, aqua sparklines, and a colour per alert kind.

![Stats report in chat](https://razekteixeira.github.io/throughput/media/stats.png)

![Alerts report in chat](https://razekteixeira.github.io/throughput/media/alerts.png)

![Live ticker on the action bar](https://razekteixeira.github.io/throughput/media/watch.gif)

## Quick start

```
/flow factory create smelter
/flow addarea smelter ~-4 ~-1 ~-4 ~4 ~2 ~4
/flow stats smelter 1h
```

## Commands

`/throughput` and the short alias `/flow` are the same command.

| Command | Who | What it does |
|---|---|---|
| `factory create <name>` | operators | New factory in your current dimension |
| `factory delete <name>` | operators | Delete a factory and its history |
| `factory list` | everyone | List factories |
| `add <factory> [pos]` | operators | Track the container you look at, or the one at `pos` (double chests: both halves) |
| `addarea <factory> <from> <to>` | operators | Track every container in a box |
| `remove <factory> [pos]` | operators | Stop tracking a container |
| `stats <factory> [1m\|10m\|1h\|10h]` | everyone | Top produced and consumed items with rates and sparklines |
| `alerts <factory> [all]` | everyone | Blocked, ran dry, missing and running out, grouped by kind; `all` lists every container (coordinates for operators only) |
| `watch <factory>` | everyone | Live ticker on your action bar |
| `unwatch` | everyone | Stop the ticker |
| `reload` | admins | Re-read `config/throughput.json` |

With a permissions mod such as LuckPerms, the nodes `throughput.use`, `throughput.manage`,
`throughput.coordinates` and `throughput.admin` take over from the operator levels (defaults 0, 2, 2, 3,
configurable in `config/throughput.json`).

## How it counts

Every second of game time, Throughput reads each tracked container and adds up the per-item change. The sum is the factory's real net production.

- **Internal moves cancel out.** A hopper moving ore from one tracked chest into a tracked furnace is a minus in one place and a plus in the other. Only smelting, crafting, and items entering or leaving the factory change the totals.
- **No phantom consumption.** A container that unloads or breaks contributes nothing, so an unloaded chunk never looks like your factory ate everything. It shows up as MISSING in alerts instead.
- **Double chests count once.** Adding a double chest tracks both halves, and each half is read on its own.
- **Game time, saved with the world.** Rates are per minute of game time and history survives restarts and pauses.

## Compatibility

- Minecraft **26.3**, Fabric Loader **0.19.5+**, **Fabric API**, Java **25**.
- **Server:** required. **Client:** optional (vanilla clients can join).
- Works with modded containers that expose Fabric item storage (the Transfer API).
- Tracks items. Fluids and energy are not tracked yet.

## FAQ

**Do players need to install it?**
No. It runs on the server and sends plain chat and action-bar text.

**Will it slow my server down?**
It only reads the containers you add, once per second of game time. Server owners can cap `addarea` box size and containers per factory in the config.

**Does it work with my storage mod?**
If the block exposes Fabric item storage, yes. Most tech mods do.

**Fluids or energy?**
Not yet.

**Are enchanted or renamed items tracked separately?**
No. Items are grouped by item id.

**Can it run in singleplayer?**
Yes. Install Fabric Loader for 26.3, put Throughput and Fabric API in your mods folder, and open a world with commands allowed.

## Links

- Website: https://razekteixeira.github.io/throughput/
- Source: https://github.com/razekteixeira/throughput
- Issues: https://github.com/razekteixeira/throughput/issues

Licensed under Apache-2.0.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
