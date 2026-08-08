# AyinChallenge

A Paper 26.2 plugin for team-based challenge runs: **all players share one health pool**, are **physically chained together**, and must work as a team to kill the Ender Dragon. If any player dies, the whole team wipes and the run is over.

## Features

- **Shared Health** — one health pool for the entire team. Damage to any player is mirrored to everyone; the team dies together.
- **Sponsor Regeneration** — vanilla natural regen is disabled. A custom heartbeat picks a single "Sponsor" player whose hunger/saturation pays for team healing (marked with a green ❤), avoiding the multi-player food-drain multiplier bug.
- **Chained Together** — players are linked in random order by invisible anchor entities and a per-tick physics pass pulls the team back together when anyone strays past the chain's max distance.
- **Run Lifecycle** — lobby → countdown → run, with a speedrun timer, persistent death/attempt stats, and a configurable world reset on wipe.
- **World Reset** — the challenge runs in a dedicated fake overworld. On wipe, the plugin can unload, delete (or back up), and regenerate the overworld/nether/end with a fresh seed.
- **Run Summary** — after a wipe, broadcasts the run's "Support" (most healing) and "Victim" (most damage taken).

## Requirements

- **Paper 26.2** (or a compatible fork)
- **Java 25**

## Commands

All commands are under `/ayinchallenge` (aliases: `/ac`, `/ayin`).

| Command | Permission | Description |
|---|---|---|
| `/ayinchallenge start` | `ayinchallenge.admin` | Recreate the challenge world and return to the lobby |
| `/ayinchallenge startconfirm` | `ayinchallenge.admin` | Begin the lobby countdown and start the run |
| `/ayinchallenge stop` | `ayinchallenge.admin` | Stop the current run |
| `/ayinchallenge reset` | `ayinchallenge.admin` | Manually trigger a world reset |
| `/ayinchallenge status` | `ayinchallenge.use` | Show run state, participants, timer, and shared HP |
| `/ayinchallenge timer` | `ayinchallenge.use` | Show the current run time |
| `/ayinchallenge stats` | OP | Get/set run counter and per-player death counts |
| `/ayinchallenge reload` | `ayinchallenge.admin` | Reload `config.yml` |

## Permissions

- `ayinchallenge.use` — basic status/timer commands (default: everyone)
- `ayinchallenge.admin` — start/stop/reset/reload (default: op, grants `use` + `debug`)
- `ayinchallenge.debug` — reserved for debug logging (default: op)

## Configuration

Everything lives in `config.yml`, organized by feature area:

- `shared-health` — max health, sync precision, totem/potion behavior
- `chain` — max distance, pull strength, leash refresh, anchor visuals
- `world-reset` — reset mode (`NONE`/`INTERNAL`/`FAHARE_COMPAT`), auto-reset on wipe, backups, challenge/holding world names
- `lobby` — border sizes and countdown length
- `timer` / `display` / `death-tracking` / `messages` — UI and messaging

## World Reset

The challenge runs in a dedicated world (default `ayin_run`) so the primary server world is never touched:

- **INTERNAL** — the plugin unloads, deletes (or backs up with `backup-before-reset: true`), and regenerates the world in-process with a new seed.
- **FAHARE_COMPAT** — hands the reset to an external Fahare-compatible plugin via `fahare-reset-command`.
- **NONE** — no automatic reset; the world must be reset manually.

If you previously ran the challenge in `world`, rename your world folder to the configured `challenge-world-name` (server stopped) or change the config — the primary server world cannot be used for in-process resets.

## Build

```bash
./gradlew build
```

Produces `build/libs/AyinChallenge-1.0.0.jar`. To launch a local test server:

```bash
./gradlew runServer
```
