# SharedChain

SharedChain is a Paper 26.2 plugin for team-based challenge runs. All players share one health pool and are physically chained together. The team wins by killing the Ender Dragon; if any player dies, the whole team wipes and the run ends.

## Features

- **Shared Health**: one health pool for the entire team. Damage to any player is mirrored to everyone.
- **Sponsor Regeneration**: vanilla natural regen is disabled. A heartbeat task picks a single Sponsor player whose hunger and saturation pay for team healing (marked with a green heart), avoiding the multi-player food-drain multiplier bug.
- **Chained Together**: players are linked in random order by invisible anchor entities. A per-tick physics pass pulls the team back together when anyone strays past the chain's max distance.
- **Run Lifecycle**: lobby, countdown, run, with a speedrun timer, persistent death and attempt stats, and a configurable world reset on wipe.
- **World Reset**: the challenge runs in a dedicated fake overworld. On wipe, the plugin can unload, delete (or back up), and regenerate the overworld, nether, and end with a fresh seed.
- **Run Summary**: after a wipe, broadcasts the run's Support (most healing) and Victim (most damage taken).

## Requirements

- Paper 26.2 (or a compatible fork)
- Java 25

## Commands

All commands are under `/sharedchain` (aliases: `/sc`, `/chain`).

| Command | Permission | Description |
|---|---|---|
| `/sharedchain start` | `sharedchain.admin` | Recreate the challenge world and return to the lobby |
| `/sharedchain startconfirm` | `sharedchain.admin` | Begin the lobby countdown and start the run |
| `/sharedchain stop` | `sharedchain.admin` | Stop the current run |
| `/sharedchain reset` | `sharedchain.admin` | Manually trigger a world reset |
| `/sharedchain status` | `sharedchain.use` | Show run state, participants, timer, and shared HP |
| `/sharedchain timer` | `sharedchain.use` | Show the current run time |
| `/sharedchain stats` | OP | Get/set run counter and per-player death counts |
| `/sharedchain reload` | `sharedchain.admin` | Reload `config.yml` |

## Permissions

- `sharedchain.use`: basic status and timer commands (default: everyone)
- `sharedchain.admin`: start, stop, reset, reload (default: op; grants `use` and `debug`)
- `sharedchain.debug`: reserved for debug logging (default: op)

## Configuration

Everything lives in `config.yml`, organized by feature area:

- `shared-health`: max health, sync precision, totem and potion behavior
- `chain`: max distance, pull strength, leash refresh, anchor visuals
- `world-reset`: reset mode (`NONE`/`INTERNAL`/`FAHARE_COMPAT`), auto-reset on wipe, backups, challenge and holding world names
- `lobby`: border sizes and countdown length
- `timer`, `display`, `death-tracking`, `messages`: UI and messaging

## World Reset

The challenge runs in a dedicated world (default `ayin_run`), so the primary server world is never touched:

- **INTERNAL**: the plugin unloads, deletes (or backs up with `backup-before-reset: true`), and regenerates the world in-process with a new seed.
- **FAHARE_COMPAT**: hands the reset to an external Fahare-compatible plugin via `fahare-reset-command`.
- **NONE**: no automatic reset; the world must be reset manually.

If the challenge previously ran in `world`, rename the world folder to the configured `challenge-world-name` (server stopped) or change the config. The primary server world cannot be used for in-process resets.

## Build

```bash
./gradlew build
```

Produces `build/libs/SharedChain-1.0.0.jar`. To launch a local test server:

```bash
./gradlew runServer
```
