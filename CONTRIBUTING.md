# Contributing to Throughput

Thanks for helping. Issues, ideas and pull requests are all welcome.

## Setup

- JDK 25 (any distribution). Gradle comes with the wrapper.
- No Minecraft account is needed: Loom downloads the game for development.

```bash
./gradlew build              # compile + unit tests + server GameTests
./gradlew runServer          # dev server in ./run
./gradlew runClient          # dev client (offline account)
./gradlew runClientGameTest  # real client; regenerates screenshots, opens a window
```

## Layout

| Path | What lives there |
|---|---|
| `src/main/java/.../core` | Pure logic with no Minecraft classes: history rings, factory accounting, alerts, settings, save format. Unit tested. |
| `src/main/java/...` | Thin Minecraft layer: container sampling (Transfer API + vanilla fast path), SavedData, commands, reports. |
| `src/test` | JUnit tests for `core` and config loading. |
| `src/gametest` | Server GameTests (`ThroughputGameTests`) and the client capture (`client/ThroughputCaptures`). |
| `site/` | GitHub Pages site. `branding/` holds the icon source, catalogue copy and `make_media.sh`. |

## Rules for changes

- **Every behaviour change comes with a test that would fail without it.** Prefer `core` unit tests;
  use a GameTest when real blocks, commands or persistence are involved.
- **Saved data:** changing what is written means bumping `VersionedState.FORMAT` and adding a
  migration. Never let a decode throw; unreadable data must stay quarantined, not discarded.
- **Performance:** sampling runs on the server thread. If you touch `ContainerSampler` or
  `TrackedFactory.applySample`, include a before/after `/flow factory list` timing from a large factory.
- **Server-side only:** no new blocks, items, packets or registries, so vanilla clients keep working.
- Code style: tabs, and match the surrounding code. Comments explain why, not what.

## Pull requests

1. Open an issue first for anything bigger than a bug fix, so we can agree on the approach.
2. Keep the PR focused, run `./gradlew build`, and fill in the template.
3. If chat output changes, re-run `./gradlew runClientGameTest && branding/make_media.sh` and
   update the chat panel text in `site/index.html` from the same run's log.

## Releases (maintainers)

One-time setup: the Modrinth project `throughput` must exist and be approved, the `MODRINTH_TOKEN`
repository secret must be set, and GitHub Pages must use the "GitHub Actions" source. Deploy the
site before the first Modrinth sync, because the Modrinth description loads its images from Pages.

1. Move the `Unreleased` notes in `CHANGELOG.md` under the new version and set `version` in
   `gradle.properties` (`-beta.N` / `-alpha.N` suffixes pick the Modrinth channel).
2. Tag `vX.Y.Z` and push the tag. The release workflow builds, runs every test, creates the GitHub
   release and uploads to Modrinth when the `MODRINTH_TOKEN` secret is set.

By contributing you agree that your contributions are licensed under the Apache License 2.0.
