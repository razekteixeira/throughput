# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0-beta.1] - 2026-10-08

First public release, for Minecraft 26.3 on Fabric.

### Added
- Factories: named groups of containers, created with `/throughput` or the alias `/flow`.
- Per-item produced and consumed rates over 1m, 10m, 1h and 10h windows with chat sparklines.
- Alerts grouped by kind: blocked outputs, containers that ran dry, missing containers, and
  items running out with a time-to-empty estimate. `alerts <factory> all` lists every container.
- Live action bar ticker with `watch` / `unwatch`.
- Works with any block exposing Fabric item storage; fast path for vanilla containers.
- `config/throughput.json` with sampling, alert, limit and permission settings, and `/flow reload`.
- Permission nodes through fabric-permissions-api (bundled), with vanilla level fallback.
- Versioned save format: unreadable or newer data is kept unchanged and the mod goes read-only.
- `/flow factory list` shows how long each factory's last sample took.
