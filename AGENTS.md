# throughput

Agent guide for this project.

<!-- BEGIN adopt-agentic-sdlc: managed region, edits here are replaced -->
## SDLC method (central)

This project adopts the central Agentic SDLC method. Source of truth for the
method (profiles, phases, G0 Intake ready to G8 Learning captured gates,
operational patterns):
`https://github.com/brunofgsaraiva/agentic-suite-agnostic` (start with `00 - Map/Vault map.md`).
Adapt, do not copy: the central method governs the form; this project governs
the local instantiation (stack, commands, thresholds).

- adopted agentic_sdlc_version: 2026-10-15+5
- Deltas since then: `https://github.com/brunofgsaraiva/agentic-suite-agnostic/CHANGELOG.md` (focus on the
  "Downstream impact" section).
- Local adaptation (when it exists): see local instantiation note.
- Knowledge sync mode: repo-only.
- Knowledge navigation scale: small.
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
<!-- END adopt-agentic-sdlc -->
