# Codebase Map: AyinChallenge (Post-Stabilization)

## Package Structure & Overview

`me.ayinaki.ayinchallenge`
├── `AyinChallenge.java` (Main JavaPlugin entry point & service coordinator)
├── `chain`
│   └── `ChainService.java` (Leash-based chain physics & bat anchor entities)
├── `command`
│   └── `AyinChallengeCommand.java` (Command executor & permission-filtered tab completer)
├── `death`
│   ├── `DeathInfo.java` (Record storing death event details & Adventure components)
│   └── `DeathTrackerService.java` (Player death count persistence & scoreboard sync)
├── `display`
│   └── `UserInterfaceService.java` (Adventure BossBar, tab list, & scoreboard manager)
├── `finish`
│   └── `RunFinishDetector.java` (Ender Dragon death listener for run completion)
├── `health`
│   └── `SharedHealthService.java` (Shared team HP, fast/slow regeneration, & sponsor exhaustion)
├── `listener`
│   ├── `DeathListener.java` (Player death & team wipe trigger listener)
│   ├── `RedirectionListener.java` (Limbo redirection listener during world reset)
│   ├── `RunListener.java` (World protection, gamerule enforcement, & join/quit events)
│   └── `SharedHealthListener.java` (Damage, potion, & totem event listener)
├── `lobby`
│   └── `LobbyService.java` (Pre-run lobby setup, countdown task, & shutdown hook)
├── `reset`
│   ├── `HoldingWorldService.java` (Limbo void world manager)
│   ├── `ResetCoordinator.java` (Asynchronous world reset sequence & Folia region scheduler integration)
│   ├── `WorldLifecycleService.java` (Async world directory deletion & Paper world creation)
│   └── `WorldResetService.java` (Reset mode routing: INTERNAL vs FAHARE_COMPAT)
├── `run`
│   ├── `RunManager.java` (Central state machine, typed GameRule management, & player resets)
│   ├── `RunState.java` (Enum: IDLE, STARTING, RUNNING, WIPED, FINISHED, RESETTING)
│   └── `SharedState.java` (Thread-safe team health container)
├── `stats`
│   └── `RunStatsService.java` (Per-run damage and healing statistics tracking)
├── `timer`
│   └── `SpeedrunTimerService.java` (Run timer, display formatting, & tick task)
└── `util`
    └── `ComponentUtil.java` (MiniMessage parsing utility)

---

## Detailed Class Responsibilities & Dependencies

### Core & Bootstrapping
- **`AyinChallenge`**
  - **Responsibilities:** Plugin lifecycle (`onEnable`, `onDisable`), service initialization, asynchronous atomic stats loading/saving (`stats.yml`), world validation (`fakeOverworld`, `limbo`), run wipe handling (`handleRunWipe`), post-run summary display.
  - **Dependencies:** Instantiates and holds references to all core services (`RunManager`, `LobbyService`, `WorldResetService`, `DeathTrackerService`, `RunStatsService`, `ChainService`, `UserInterfaceService`, etc.).

### Command Subsystem (`me.ayinaki.ayinchallenge.command`)
- **`AyinChallengeCommand`**
  - **Responsibilities:** Executor and tab-completer for `/ayinchallenge` (aliases: `/ac`, `/ayin`). Handles subcommands: `start`, `startconfirm`, `stop`, `reset`, `status`, `timer`, `reload`, `stats`. Tab completions are filtered by permission (`ayinchallenge.admin`). Offline player queries use async resolution.
  - **Dependencies:** `AyinChallenge`, `RunManager`, `ResetService`, `LobbyService`, `SpeedrunTimerService`, `DeathTrackerService`, `UserInterfaceService`, `ComponentUtil`.

### World Reset Subsystem (`me.ayinaki.ayinchallenge.reset`)
- **`WorldResetService`**
  - **Responsibilities:** High-level reset trigger. Routes reset request based on configuration (`INTERNAL` vs `FAHARE_COMPAT`).
  - **Dependencies:** `HoldingWorldService`, `WorldLifecycleService`, `ResetCoordinator`.
- **`ResetCoordinator`**
  - **Responsibilities:** Executes multi-step world reset sequence: teleports players to Limbo in spectator mode, unloads non-limbo worlds on the main thread, offloads world folder deletion asynchronously, regenerates world with shared seed, and restores player survival state via Folia-compatible `GlobalRegionScheduler`.
  - **Dependencies:** `AyinChallenge`, `HoldingWorldService`, `WorldLifecycleService`, `RunManager`, `ChainService`, `UserInterfaceService`.
- **`WorldLifecycleService`**
  - **Responsibilities:** Low-level Paper/Bukkit world management: unloading worlds, clearing non-player entities, asynchronously deleting world directories (without blocking `Thread.sleep`), backing up world folders, and logging region file diagnostics.
  - **Dependencies:** Paper/Bukkit API (`Bukkit`, `WorldCreator`, `World`).
- **`HoldingWorldService`**
  - **Responsibilities:** Creates and provides access to the flat End-biome void holding world (`ayinchallenge:limbo`) where players reside during world resets.
  - **Dependencies:** `AyinChallenge`, `WorldCreator`.

### Lobby Subsystem (`me.ayinaki.ayinchallenge.lobby`)
- **`LobbyService`**
  - **Responsibilities:** Sets up pre-run lobby in target world (shrinks world border to 10 blocks, locks world time to 0, teleports online players to spawn, activates chain service, broadcasts click-to-start prompt). Executes countdown timer with title overlays and note-block sound effects before expanding world border to active run size (100,000 blocks) and starting run. Includes `shutdown()` method to cancel pending tasks on reload/disable.
  - **Dependencies:** `AyinChallenge`, `RunManager`, `ChainService`, `UserInterfaceService`, `ComponentUtil`.

### Stats & Death Tracking Subsystem (`me.ayinaki.ayinchallenge.stats` & `me.ayinaki.ayinchallenge.death`)
- **`DeathTrackerService`**
  - **Responsibilities:** Tracks player death counts across runs, persists deaths to `stats.yml` asynchronously, updates scoreboard player list death counters.
  - **Dependencies:** `AyinChallenge`, `UserInterfaceService`.
- **`RunStatsService`**
  - **Responsibilities:** In-memory tracking of per-run healing provided (`hpHealed`) and damage absorbed (`damageTaken`). Identifies top sponsor (healer) and top sponge (victim) for post-run summary.
  - **Dependencies:** `AyinChallenge`.
- **`DeathInfo`**
  - **Responsibilities:** Record storing details of a run-ending death (dead player, killer entity, damage cause, timestamp, location, formatted Adventure component description).
  - **Dependencies:** Adventure API, Bukkit `EntityDamageEvent`.

### Run Management & State (`me.ayinaki.ayinchallenge.run`)
- **`RunManager`**
  - **Responsibilities:** Central state machine (`RunState`: `IDLE`, `STARTING`, `RUNNING`, `WIPED`, `FINISHED`, `RESETTING`). Manages run timing, active participants set, Paper typed gamerules (`DO_IMMEDIATE_RESPAWN`, `NATURAL_REGENERATION`, `hardcore`), player state resets (clearing inventory/enderchest/advancements), and async run state persistence to `stats.yml`.
  - **Dependencies:** `AyinChallenge`, `SharedState`, `SpeedrunTimerService`, `SharedHealthService`, `RunFinishDetector`, `LobbyService`.
- **`RunState`**
  - **Responsibilities:** Enum defining run states: `IDLE`, `STARTING`, `RUNNING`, `WIPED`, `FINISHED`, `RESETTING`.
- **`SharedState`**
  - **Responsibilities:** Thread-safe container for shared team health and max health.

### Health, Timer, Chain & UI Subsystems
- **`SharedHealthService`**: Heartbeat task managing fast/slow shared regeneration, consuming sponsor hunger/exhaustion, and syncing player health attributes across participants.
- **`SpeedrunTimerService`**: Manages run timer tick tasks and formatting elapsed/total time strings.
- **`ChainService`**: Manages leash-based player chain physics and bat anchor entities.
- **`UserInterfaceService`**: Manages modern Kyori Adventure BossBars (`net.kyori.adventure.bossbar.BossBar`), tab list headers/footers, action bar timer, and death scoreboard objectives.
- **`RunFinishDetector`**: Listens for Ender Dragon death events to trigger run completion.
- **`ComponentUtil`**: MiniMessage parsing utility.

---

## Service Dependency Matrix

```
AyinChallenge (Main)
 ├──> RunManager
 │     ├──> SharedState
 │     ├──> SpeedrunTimerService
 │     ├──> SharedHealthService
 │     └──> RunFinishDetector
 ├──> LobbyService
 │     ├──> RunManager
 │     ├──> ChainService
 │     └──> UserInterfaceService
 ├──> WorldResetService
 │     ├──> HoldingWorldService
 │     ├──> WorldLifecycleService
 │     └──> ResetCoordinator
 │           ├──> HoldingWorldService
 │           ├──> WorldLifecycleService
 │           └──> RunManager
 ├──> DeathTrackerService ──> UserInterfaceService
 ├──> RunStatsService
 └──> AyinChallengeCommand ──> (All Services)
```
