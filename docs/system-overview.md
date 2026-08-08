# System Overview & Architecture

This document provides a high-level architectural overview and runtime flow analysis for the AyinChallenge plugin.

---

## 1. Component Sitemap & Responsibilities

```
me.ayinaki.ayinchallenge
│
├── AyinChallenge.java            # Main JavaPlugin entrypoint; service container & lifecycle initialization
│
├── chain/
│   └── ChainService.java         # Chain order management, bat anchor spawning, leash updating, physics pull calculations
│
├── health/
│   └── SharedHealthService.java  # Team shared health sync pool & custom 10-tick food/saturation heartbeat task
│
├── run/
│   ├── RunManager.java           # Central run state engine, gamerule manager, participant set, persistence
│   ├── RunState.java             # Enum: IDLE, RUNNING, STARTING, FINISHED, WIPED, RESETTING
│   └── SharedState.java          # Thread-safe synchronized health & max-health state container
│
├── reset/
│   ├── ResetCoordinator.java     # Synchronous world unload, file deletion, and re-creation pipeline
│   ├── WorldLifecycleService.java# Low-level directory deletion, backups, and WorldCreator utilities
│   ├── WorldResetService.java    # Bridge service for INTERNAL vs FAHARE_COMPAT reset modes
│   └── HoldingWorldService.java  # Limbo world creation and retrieval
│
├── lobby/
│   └── LobbyService.java         # Pre-run lobby border setup, time-lock task, 5-second start countdown
│
├── display/
│   └── UserInterfaceService.java # Combined management of Attempt BossBar, Action Bar timer, Tab List, Scoreboard
│
├── listener/
│   ├── RunListener.java          # Lobby protection events, block break/place, damage/food cancellation, anchor safety
│   ├── SharedHealthListener.java # Damage interceptor, heal interceptor (cancels vanilla satiated regen), totem handler
│   ├── DeathListener.java        # Player death interceptor -> triggers run wipe & death stats tracking
│   └── RedirectionListener.java # Redirects joins, respawns, and portals away from real overworld to fake overworld
│
├── finish/
│   └── RunFinishDetector.java    # Ender dragon death listener & 10-second delay finish task
│
├── death/
│   ├── DeathInfo.java            # Immutable record holding dead player, killer, cause, location, timestamp
│   └── DeathTrackerService.java  # Persistent per-player death counter backed by stats.yml
│
├── stats/
│   └── RunStatsService.java      # Per-run tracking of HP healed (sponsor) and damage taken (victim)
│
├── timer/
│   └── SpeedrunTimerService.java # 1-tick interval timer display formatter for run duration and total elapsed time
│
├── command/
│   └── AyinChallengeCommand.java # Admin executor (/ayinchallenge start, stop, reset, status, stats, reload)
│
└── util/
    └── ComponentUtil.java        # MiniMessage adventure text deserialization & placeholder helper
```

---

## 2. Runtime Execution Flow Diagrams

### 2.1 World Reset & Lobby Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Cmd as AyinChallengeCommand
    participant Reset as ResetCoordinator
    participant RunMgr as RunManager
    participant Limbo as HoldingWorldService
    participant Lobby as LobbyService
    participant Chain as ChainService

    Admin->>Cmd: /ayinchallenge start or reset
    Cmd->>Reset: initiateReset()
    Reset->>RunMgr: setState(RunState.RESETTING)
    Reset->>Chain: deactivate()
    Reset->>Limbo: getOrCreateHoldingWorld()
    Reset->>RunMgr: Teleport players to Limbo (Spectator)
    Reset->>Reset: Unload Overworld/Nether/End
    Reset->>Reset: Delete world folders on disk & Re-create with new Seed
    Reset->>RunMgr: onWorldResetComplete(newWorld)
    RunMgr->>Lobby: setupLobby(newWorld)
    Lobby->>Lobby: Set Border to 10m & Lock Time to 0
    Lobby->>Chain: activateFor(onlinePlayers)
    Lobby->>Admin: Broadcast [Start] Button Prompt
```

### 2.2 Run Start & Active Game Loop

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Lobby as LobbyService
    participant RunMgr as RunManager
    participant Health as SharedHealthService
    participant Timer as SpeedrunTimerService
    participant Chain as ChainService

    Admin->>Lobby: /ayinchallenge startconfirm
    Lobby->>Lobby: startCountdown() [5s Countdown Titles & Sound]
    Note over Lobby: Countdown reaches 0
    Lobby->>Lobby: Expand Border to 100,000m & Unlock Time
    Lobby->>RunMgr: start()
    RunMgr->>RunMgr: setState(RunState.RUNNING)
    RunMgr->>RunMgr: Apply Hardcore & Immediate Respawn Rules
    RunMgr->>Health: syncHealth()
    RunMgr->>Timer: start()
    
    loop Every Server Tick (20 Hz)
        Chain->>Chain: updateAnchorsAndLeashes() & applyPhysics()
    end

    loop Every 10 Ticks (2 Hz)
        Health->>Health: tickRegeneration() (Fast/Slow Sponsor Check)
    end
```

### 2.3 Damage & Shared Health Sync Pipeline

```mermaid
sequenceDiagram
    autonumber
    actor Mob
    participant Victim as Player (Victim)
    participant Listener as SharedHealthListener
    participant Health as SharedHealthService
    participant State as SharedState
    participant Team as All Chained Players

    Mob->>Victim: Deals Damage (Vanilla Damage Pipeline)
    Note over Victim: Plays hit sound, knockback, I-frames
    Listener->>Listener: onDamage (MONITOR priority)
    Listener->>Listener: Schedule task on next tick
    Note over Listener: Next Server Tick Execution
    Listener->>Health: syncFromPlayerHealth(Victim)
    Health->>State: setHealth(Victim.getHealth())
    Health->>Health: syncHealth()
    Health->>Team: Set player.setHealth(clampedHealth) & maxHealth attribute
    Listener->>Team: Broadcast damage message: "[A] Victim took X ❤ damage."
```

### 2.4 Run Wipe Flow

```mermaid
sequenceDiagram
    autonumber
    actor World
    participant DeadPlayer as Player
    participant DListener as DeathListener
    participant Main as AyinChallenge
    participant RunMgr as RunManager
    participant Chain as ChainService

    World->>DeadPlayer: Fatal Damage (HP reaches 0)
    DListener->>DListener: PlayerDeathEvent
    DListener->>DListener: Cancel Vanilla Death Message & Record DeathInfo
    DListener->>Main: handleRunWipe(DeathInfo)
    Main->>RunMgr: wipe(info)
    RunMgr->>RunMgr: setState(RunState.WIPED)
    Main->>Main: Play Illusioner Wipe Sound & Broadcast Wipe Message
    Main->>Main: Display Run Summary (Top Sponsor & Top Victim)
    Main->>DeadPlayer: Drop Inventory & Convert Participants to Spectator
    Main->>Chain: deactivate()
    Main->>World: Broadcast Clickable [Reset] Link to OPs
```

---

## 3. Threading & Synchronization Model

- **Main Thread Execution:** All Bukkit API calls (teleportation, entity spawning, leash manipulation, inventory modification, gamerule changes, scoreboard updates) run strictly on the main server thread.
- **Synchronized State Wrappers:** `SharedState` uses Java `synchronized` method modifiers for read/write access to `health` and `maxHealth`. `SharedHealthService.syncHealth()` utilizes an internal boolean lock (`syncing`) to prevent re-entrant recursion during health modification.
- **Concurrent Collections:** `RunManager` utilizes `ConcurrentHashMap.newKeySet()` for `participants` to safeguard against concurrent modification during player joins/quits.
