# AyinChallenge

AyinChallenge is a production-ready Paper plugin for Paper 26.2 designed for multiplayer speedruns. All players share a single health pool and food system. If one player dies, everyone dies, and the run ends.

## Features
- **Shared Health**: One health pool for all participants.
- **Shared Food/Saturation**: Shared hunger and saturation behavior.
- **Shared Exhaustion**: Energy consumption is shared across the team.
- **Natural Regen Control**: Natural regeneration is centralized to prevent per-player stacking.
- **Speedrun Timer**: Built-in timer with multiple display options.
- **Death Tracking**: Detailed recording of run-ending deaths and wipe counts in the tab list.
- **World Reset**: Support for automatic world resets on wipe, including compatibility with the Fahare plugin.
- **Modern APIs**: Uses Adventure Components and MiniMessage for all user-facing text.

## Commands
- `/ayinchallenge start`: Start a new challenge run.
- `/ayinchallenge stop`: Stop the current run.
- `/ayinchallenge reset`: Manually trigger a world reset.
- `/ayinchallenge status`: View the current run status and participants.
- `/ayinchallenge timer`: View the current run timer.
- `/ayinchallenge reload`: Reload the plugin configuration.

## Permissions
- `ayinchallenge.use`: Allows using basic status and timer commands. (Default: everyone)
- `ayinchallenge.admin`: Allows starting/stopping/resetting runs. (Default: op)
- `ayinchallenge.debug`: Allows access to debug logs if enabled. (Default: op)

## Configuration
Detailed configuration options are available in `config.yml`, including:
- Shared health/food settings.
- Timer display modes (ACTION_BAR, BOSS_BAR, etc.).
- World reset modes (NONE, INTERNAL, FAHARE_COMPAT).

## Architecture
AyinChallenge uses a service-oriented architecture:
- **RunManager**: Manages the core state machine (IDLE, RUNNING, etc.).
- **SharedState**: Atomic storage for shared team attributes.
- **Services**: Modular services for health, food, timer, and display logic.
- **Listeners**: Isolated event handlers that update the shared state.

## World Reset Integration
AyinChallenge uses a dedicated world architecture to safely reset without server restarts.
- **Dedicated Challenge World**: The challenge runs in a separate world (default: `ayin_run`) rather than the primary server world (`world`). This allows the plugin to safely unload, delete, and regenerate the world.
- **Primary World as Hub**: The primary server world is kept permanently loaded as a safe fallback or hub world.
- **INTERNAL Mode**: Automatically moves players to a holding world, unloads the challenge world, deletes/backs up the data, and generates a new world with a new seed.
- **FAHARE_COMPAT Mode**: Coordinates with the Fahare plugin for reset operations.

## Migration Note
If you are upgrading from a version that used `world` as the challenge world:
1. Change `challenge-world-name` in `config.yml` to a new name like `ayin_run`.
2. The plugin will automatically create this new world on startup.
3. If you want to keep your existing world, rename your `world` folder to `ayin_run` (while the server is stopped) or update the config accordingly. Note that the primary world defined in `server.properties` CANNOT be used for in-process resets.

## Installation
1. Place the `AyinChallenge.jar` in your server's `plugins` folder.
2. (Optional) Install `Fahare` for enhanced world reset behavior.
3. Start the server and configure `config.yml`.
4. Run `/ayinchallenge start` to begin.
