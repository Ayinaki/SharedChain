# Future Development Notes

This document provides concrete guidance for integrating new features into the current architecture of the AyinChallenge plugin.

---

## 1. Developing New Gameplay Mechanics

### 1.1 Events and Thread Safety
- **Avoid `PlayerMoveEvent`:** Currently, movement distance checks are efficiently processed in `ChainService.java` via a 1-tick repeating task using `distanceSquared` as a spatial guard. Do not introduce new `PlayerMoveEvent` listeners for distance tracking. Follow the existing pattern: schedule a tick task and only run math-heavy operations if bounding box or squared-distance thresholds are met.
- **Main Thread Requirement:** All Bukkit API calls (teleportation, velocity changes, entity spawning, inventory management, world loading/unloading) currently run safely on the server main thread. If you add database queries or complex file I/O, execute them asynchronously and return to the main thread via `Bukkit.getGlobalRegionScheduler()` before interacting with the Bukkit API.

### 1.2 State Modifiers
- When creating new features that alter health, food, or combat:
  - Intercept vanilla events at `MONITOR` priority and defer action by 1 tick using `Bukkit.getScheduler().runTask()` (as seen in `SharedHealthListener.java`). This preserves vanilla mechanics like damage knockback, hit sounds, and I-frames before overriding the health pool.
  - Do not use raw velocity `setVelocity()` changes for anti-cheat compatibility unless an exemption is applied or you check the player's ground state.

---

## 2. Extending the Data & Config Models

### 2.1 Configuration
- When adding new keys to `config.yml`, ensure the default values in `config.yml` match the fallback values embedded in the Java source code (via `getConfig().getXYZ("key", fallback)`).
- Document new keys immediately in `docs/config-reference.md`.

### 2.2 Persistence
- Currently, `stats.yml` is saved synchronously on the main thread during events like player death. Before adding heavy logging or extensive data tracking, refactor `AyinChallenge.saveStats()` to execute disk I/O asynchronously to avoid tick-lag spikes.
- Use `ConcurrentHashMap` for any volatile maps accessed across multiple scopes (like `participants` in `RunManager.java`).

---

## 3. World and Reset Integration

### 3.1 Reset Sequences
- If introducing custom dimensions, they must be manually hooked into `ResetCoordinator.java`'s deletion array. The current implementation only deletes loaded worlds.
- Remove blocking `Thread.sleep` from `WorldLifecycleService` file deletion loops before deploying on a live Windows production environment.

### 3.2 UI and Action Bar
- The `SpeedrunTimerService.java` updates UI elements at 20Hz. If introducing new BossBars or action bar trackers, attach them to this existing tick loop rather than spinning up independent scheduled tasks to conserve packet throughput, but consider throttling the UI update rate to 4-5Hz.

---

## 4. API Standards
- **Java 25 and Paper 1.21+**: Keep your Gradle toolchain pointed at `JavaLanguageVersion.of(25)`.
- Use modern Paper `GameRule` typed methods (e.g. `world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, Boolean.TRUE)`) over the deprecated untyped Bukkit string keys.
- Use Kyori Adventure components for all chat and UI messaging. Use `ComponentUtil.java` to load and parse MiniMessage formats from config.
