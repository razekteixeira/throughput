# Source Code Agent Guide

This repository contains the source code for Throughput. The sibling vault
repository owns the project authority map and durable agent-facing synthesis.
The project rules for code work are in the "Throughput" section at the end of
this file; they apply to every agent (Claude Code, Codex, Cursor, others).

This is a public repository. The vault is local only (no remote) and private.
Cloud sessions and worktrees outside `personal/throughput/` will not find it:
everything needed for code work is in this file, `README.md` and
`CONTRIBUTING.md`. Never copy vault content into tracked files.

## First Read

1. Read this file.
2. Read `../throughput-vault/Welcome.md`.
3. Read `../throughput-vault/Vault Map.md`.
4. Read `../throughput-vault/00 - Meta/Agent Guide.md`.
5. Read `../throughput-vault/00 - Meta/Source Authority.md` and
   `../throughput-vault/00 - Meta/Agentic Project Profile.json`.
6. Read only the notes relevant to the task.

## Source Hierarchy

1. User request in the current conversation.
2. Source-code repository rules in this file.
3. The authority map and governed vault notes under `../throughput-vault/`.
4. The owner's global agent instructions, when the runtime loads them.

## SDLC method (central)

This project adopts the central Agentic SDLC method. Source of truth for the
method (profiles, phases, G0 Intake ready to G8 Learning captured gates,
operational patterns):
`../../../agentic-suite-agnostic/` (start with `00 - Map/Vault map.md`).
Adapt, do not copy: the central method governs the form; this project governs
the local instantiation (stack, commands, thresholds).

- adopted agentic_sdlc_version: 2026-10-20
- Deltas since then: `../../../agentic-suite-agnostic/CHANGELOG.md` (focus on the
  "Downstream impact" section).
- Without the sibling checkout, the method is at
  `https://github.com/brunofgsaraiva/agentic-suite-agnostic`.
- Local adaptation (when it exists): see local instantiation note,
  `../throughput-vault/05 - Operations/SDLC Local Instantiation.md` (gates
  mapping, thresholds, repeatability decisions). The gate commands needed for
  code work are repeated in "Commands and gates" below.
- Knowledge sync mode: authoritative-vault.
- Knowledge navigation scale: small.
- Adoption contract: `../../../agentic-suite-agnostic/ADOPTION.md`.
- Agent entry rule: for delivery or coding work, use the `agentic-sdlc` gateway
  before acting. If the runtime does not load skills automatically, read
  `../../../agentic-suite-agnostic/skills/agentic-sdlc/SKILL.md` and follow it.

<!-- agentic-project:runtime-profile:start -->
## Agentic Project runtime defaults

- Selected by default when available: `rtk-ai-cli`. Compress noisy development command output before it enters agent context.
- These are project runtime preferences, not Agentic SDLC dependencies or gate
  criteria. The user can opt out of a default for a task.
- Explicit operator modes such as `modo-compacto` remain opt-in only.
- Optional scaffold files for project type `product-tooling`: none, by declaration. Reason: Product tooling is reproduced by its own engineers from the repository's test path, so the reader-facing report adds a second, weaker surface.

<!-- agentic-project:runtime-profile:end -->

## Rules

- When reporting information to me, be extremely concise and sacrifice grammar for the sake of concision
- Use EN-GB for project knowledge unless the user asks otherwise.
- Follow the sibling vault's `00 - Meta/Source Authority.md` for persistent
  knowledge, external facts, approvals and work state.
- Preserve relative links between the source-code and vault repositories.
- Keep raw source material, working notes and derived outputs separate.
- Use `../throughput-vault/02 - Sources/raw/` as the unprocessed source inbox;
  move ingested source files to `../throughput-vault/02 - Sources/raw/processed/`
  and attachments to `../throughput-vault/02 - Sources/raw/assets/`.
- Classify documentation code by role, not extension. This repository owns
  generators, themes and documentation applications that require a build,
  dependencies, tests or deployment. Hand-authored HTML, CSS or JavaScript that
  is itself human-readable documentation may belong in the sibling vault.
- Do not edit generated documentation outputs directly. Record
  `generated_from`, `build_command` and `output_path` in the vault operations
  note.
- If the sibling vault is unavailable, do not invent or silently replace its
  decisions. Record the missing dependency and continue only with bounded code work.
- Repeatability rule: when a task, or a stable part of it, is reasonably
  expected to run more than once, the work is not complete until it leaves the
  smallest suitable deterministic path under version control. Reuse an existing
  path first. Otherwise add a script or task, hook or CI check, scheduled job,
  skill or pipeline, or a loop only when iteration and a verifiable done-rule
  are required.
  Size the mechanism against its payback before building it: state the
  expected number of runs over a named horizon, the cost of one manual run,
  and the cost of building and owning the path. When the build does not pay
  back inside that horizon, keep the task manual and record that decision
  where the next operator will look. Frequency alone does not justify
  automation, and neither does a paid subscription.
  Document the trigger and verification. Keep irreducible judgement as an
  explicit human checkpoint; do not automate an unstable process or oracle.
- Do not revert or overwrite changes from another agent. Inspect dirty state before editing.
- Keep this file as the only canonical instruction source. `CLAUDE.md` is an
  import-only pointer and must not contain separate project rules.
- Do not store credentials, secrets or private customer data in the repository.

## Writing Conventions

- Never use the em dash or en dash in human-facing deliverables (HTML pages, slide decks, DOCX/PDF, published or client-facing content) or in the sources that render into them. Prefer commas, colons, parentheses or full stops; in ranges use "to" (for example, "G0 Intake ready to G8 Learning captured"). Internal artefacts (chat responses, code, commit messages, scratch notes) are exempt.
- Short identifiers (G0 Intake ready, D40 plus readable name, R10 plus readable name) always appear with the readable name beside them (for example, "G4 Tests with teeth"), never just the code.

## Throughput

Server-side Fabric mod that turns a group of containers into a "factory" and
reports production and consumption rates, sparklines, run-out estimates and
alerts in chat. Rules below were collected on 2026-10-19 from `CONTRIBUTING.md`,
`README.md`, the pull request template, the workflows and the previous
`AGENTS.md`; `CONTRIBUTING.md` stays the human contributor guide and wins on
detail.

- Repo: https://github.com/razekteixeira/throughput (public, Apache-2.0, default branch `main`).
- Site: https://razekteixeira.github.io/throughput/ (GitHub Pages, from `site/`).
- Distribution: CurseForge project `1733679`, slug `throughput`.
- Targets Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API and Java 25;
  versions live in `gradle.properties`.

### Public repository

- Everything tracked here is public. Never commit private notes, plans,
  monetisation, outreach drafts or vault content; reference the vault only as
  the local path `../throughput-vault/`, never from README, site or listings.
- Secrets: `CURSEFORGE_TOKEN` exists only as a repository secret and is exposed
  to the publish step alone. Never put a token in `gradle.properties`, code,
  logs or screenshots.
- Security problems are reported through GitHub private vulnerability reporting
  (`SECURITY.md`), never in public issues. Only the latest release gets fixes.
- `run/`, `logs/`, `build/` and `.gradle/` are ignored local state. Never commit
  them; `tools/benchmark.sh` writes a throwaway RCON password into `run/`.

### Rules for changes

- Every behaviour change comes with a test that would fail without it. Prefer
  `core` unit tests (`src/test`); use a server GameTest (`src/gametest`) when
  real blocks, commands or persistence are involved.
- `src/main/java/.../core` is pure logic with no Minecraft classes; keep it that
  way so it stays unit testable. The Minecraft layer around it stays thin.
- Saved data: changing what is written means bumping `VersionedState.FORMAT`
  and adding a migration. Never let a decode throw; unreadable or newer data is
  kept byte for byte (quarantined, read-only), never discarded.
- Performance: sampling runs on the server thread. Changes to `ContainerSampler`
  or `TrackedFactory.applySample` include a before/after `/flow factory list`
  timing from a large factory (`tools/benchmark.sh`).
- Server-side only: no new blocks, items, packets or registries, so vanilla
  clients keep working.
- Code style: tabs, match the surrounding code; comments explain why, not what.
- Chat output changes: regenerate the media from one real client run
  (`caffeinate -dimsu ./gradlew runClientGameTest`, `branding/make_media.sh`,
  `python3 -P branding/make_banners.py`) and copy the chat panel text in
  `site/index.html` and the sample in `branding/curseforge-description.md` from
  that same run's log.
- Artwork is script-generated and labelled honestly: screenshots are real
  client captures (`ThroughputCaptures`), the 3D logo is a Blender render of the
  icon's pixels (`branding/render_logo3d.py`). Do not substitute other imagery.
- Keep `README.md` (commands, config example, measured performance) and the
  `Unreleased` section of `CHANGELOG.md` in step with user-visible changes.
- Pull requests follow `.github/pull_request_template.md`: `./gradlew build`
  passes, new behaviour has a failing-without test, saved data format unchanged
  or bumped with a migration, screenshots regenerated when chat output changed.

### Commands and gates (summary of the SDLC local instantiation)

- JDK 25 is required (CI uses Temurin 25 on ubuntu-24.04). Gradle comes with
  the wrapper; no Minecraft account is needed.
- Local gate before any PR: `./gradlew build` (compile, unit tests, server
  GameTests on a headless server). CI (`.github/workflows/build.yml`) runs the
  same on every push and pull request.
- Dev: `./gradlew runServer` (dev server in `./run`), `./gradlew runClient`,
  `./gradlew runClientGameTest` (real client, opens a window, regenerates
  screenshots), `tools/benchmark.sh` (sampling cost on 4,096 chests).
- Every code-changing request is an SDLC WorkItem unless the user asks for
  answer-only or no code changes. XS/S non-sensitive work uses fast-lane (G1
  Spec/Design ready and G2 Plan ready collapse into an inline plan); M uses the
  full gates; L needs a written spec and fresh-context review; XL is split first.
- Fast-lane never skips verification: G4 Tests with teeth always runs. G5
  Security clean is always decided: audit when touching the save format, config
  parsing, commands or permissions, the release workflow, secrets or
  dependencies; otherwise record the skip reason.
- Final response for a code-changing task: effort level and fast-lane decision,
  commands run and results, tests proving acceptance, G5 result or skip reason,
  release result or G7 skip reason, any G8 learning.
- Merges into `main` go through a pull request the owner reviews and approves.
- Site: `.github/workflows/pages.yml` publishes `site/` on a push to `main`
  that touches `site/**` (or by manual dispatch).
- Release (owner's word only): move the `Unreleased` notes in `CHANGELOG.md`
  under the new version and set `version` in `gradle.properties` (`-alpha.N`,
  `-beta.N`, `-rc.N` pick the CurseForge release type); tag `vX.Y.Z` and push the
  tag. `.github/workflows/release.yml` checks the tag matches, builds and tests,
  creates the GitHub release and uploads to CurseForge when `CURSEFORGE_TOKEN`
  is set. `./gradlew publishCurseforge` without a token is a dry run under
  `build/publishMods/`. CurseForge page setup: `branding/curseforge-listing.md`.
