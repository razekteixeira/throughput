# CurseForge launch sheet

Everything CurseForge asks for, in order. The project page, logo and gallery have no public API,
so they are entered by hand once; files are uploaded by the release workflow afterwards.

## 1. Create the project

[authors.curseforge.com](https://authors.curseforge.com) → Create project → Minecraft → Mods.

| Field | Value |
|---|---|
| Name | `Throughput` |
| Slug | `throughput` (URL: curseforge.com/minecraft/mc-mods/throughput) |
| Summary | contents of [`summary.txt`](summary.txt) |
| Description | switch the editor to **Markdown**, paste [`curseforge-description.md`](curseforge-description.md) |
| Main category | Server Utility |
| Additional categories | Utility & QoL, Technology › Automation, Map and Information |
| License | Apache License 2.0 |
| Source URL | https://github.com/razekteixeira/throughput |
| Issues URL | https://github.com/razekteixeira/throughput/issues |
| Wiki / website | https://razekteixeira.github.io/throughput/ |
| Logo | [`icon-512.png`](icon-512.png) (square PNG, at least 400×400; not WebP) |

Why these categories: the audience is server owners and automation players. It is a monitoring
tool (Server Utility, Utility & QoL) for automated production (Technology › Automation) that
reports information about your base (Map and Information). It adds no storage, so not Storage.

## 2. Settings

- **Project distribution / third-party apps: enable it.** Without it, launchers (Prism,
  ATLauncher) and server hosts cannot download the file, which matters for a server-side mod.

## 3. Images tab (gallery)

Upload in this order; mark the first one as featured.

| File | Title | Description |
|---|---|---|
| `site/media/banner.png` | Throughput | Factorio-style production stats for any Minecraft factory. |
| `site/media/gallery-night.png` | Night shift | A six-furnace smelting floor tracked as one factory. |
| `site/media/stats.png` | Rates and sparklines | /flow stats: produced and consumed per minute, with history. |
| `site/media/alerts.png` | Grouped alerts | /flow alerts: blocked outputs, emptied buffers and time-to-empty. |
| `site/media/watch.gif` | Live ticker | /flow watch: the factory's top output and input on your action bar. |
| `site/media/gallery-angle.png` | Any container | Chests, hoppers, furnaces and modded storage, read without client mods. |
| `site/media/gallery-closeup.png` | One line up close | Input chest, hopper, furnace, hopper, output chest. |

## 4. Releases

Already wired up: project ID `1733679` is in `gradle.properties`, the `CURSEFORGE_TOKEN`
repository secret is set, and the README shows the CurseForge downloads badge.

1. Tag the release: `git tag v1.0.0-beta.1 && git push origin v1.0.0-beta.1`. The workflow builds,
   runs every test, creates the GitHub release and uploads the jar to CurseForge as a beta
   (game version 26.3, Java 25, Fabric, client + server, requires Fabric API).
2. The file shows as "Under review" until a CurseForge moderator approves it.
3. If the API token is ever replaced: create it at
   [authors.curseforge.com → API tokens](https://authors.curseforge.com/#/settings/api-tokens) and
   run `gh secret set CURSEFORGE_TOKEN -R razekteixeira/throughput`.
