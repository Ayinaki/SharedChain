# Risk Register (Post-Stabilization)

This document aggregates fragile code paths, performance risks, thread-safety concerns, Paper-version-sensitive code, and their resolution status following the codebase stabilization refactoring.

---

## 1. Threading & File I/O Concerns

### 1.1 Blocking `Thread.sleep` on Server Main Thread
- **Location:** `ResetCoordinator.java` & `WorldLifecycleService.java`
- **Risk Level:** **RESOLVED** (Formerly CRITICAL)
- **Previous Issue:** `WorldLifecycleService` invoked `Thread.sleep(100)` or `Thread.sleep(500)` during Windows file deletion retries on the main server thread, freezing ticks during world resets.
- **Resolution:** Offloaded file deletion tasks to asynchronous threads (`CompletableFuture.runAsync()`) and eliminated blocking `Thread.sleep()` calls from the main thread. World re-creation steps safely return to the main thread using Paper's `GlobalRegionScheduler`.

### 1.2 Synchronous Main-Thread YAML Save
- **Location:** `DeathTrackerService.java`, `RunManager.java`, & `AyinChallenge.java`
- **Risk Level:** **RESOLVED** (Formerly HIGH)
- **Previous Issue:** `statsConfig.save(statsFile)` was executed directly on the main server thread during player death events, run state changes, and plugin shutdown.
- **Resolution:** Implemented asynchronous atomic `.tmp` file writing and replacement (`saveStatsAsync()`), offloading disk I/O away from the main server thread and preventing file corruption during unexpected crashes.

### 1.3 Blocking Mojang API Call
- **Location:** `AyinChallengeCommand.java`
- **Risk Level:** **RESOLVED** (Formerly MEDIUM)
- **Previous Issue:** `Bukkit.getOfflinePlayer(String)` triggered blocking web calls to Mojang's API on the main server thread when querying uncached usernames.
- **Resolution:** Replaced blocking name lookups with asynchronous player profile lookups and UUID-cached resolution on the main thread.

---

## 2. API Alignment & Deprecations

### 2.1 Deprecated `GameRule` API
- **Location:** `RunManager.java`, `RunListener.java`
- **Risk Level:** **RESOLVED** (Formerly LOW)
- **Previous Issue:** Used deprecated raw string `GameRule` setters/getters requiring `@SuppressWarnings("removal")`.
- **Resolution:** Replaced all occurrences with modern Paper typed `GameRule` setters (`world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, Boolean.TRUE)`).

### 2.2 Legacy BossBar Implementation
- **Location:** `UserInterfaceService.java`
- **Risk Level:** **RESOLVED** (Formerly LOW)
- **Previous Issue:** Utilized legacy Bukkit `KeyedBossBar` instead of modern Adventure `BossBar`.
- **Resolution:** Migrated bossbar management entirely to Kyori Adventure's `net.kyori.adventure.bossbar.BossBar` API (`BossBar.bossBar(...)`, `Audience.showBossBar`, `Audience.hideBossBar`).

### 2.3 Build Script Dependency Versioning
- **Location:** `build.gradle.kts`
- **Risk Level:** **RESOLVED** (Formerly LOW)
- **Previous Issue:** Targeted non-standard build artifact strings (`26.1.2.build.5-alpha`).
- **Resolution:** Standardized dependencies to target `io.papermc.paper:paper-api:26.2.build.111-stable` with Java 25 toolchain alignment.

---

## 3. Gameplay Mechanics & Balancing Issues

### 3.1 Extreme Food Drain on Regeneration Sponsor
- **Location:** `SharedHealthService.java`
- **Risk Level:** **MEDIUM**
- **Issue:** Adds `6.0` exhaustion points per `1.0` HP restored. This drains hunger rapidly during intense combat.
- **Mitigation:** Exhaustion multiplier is configured and balanced in code/config.

### 3.2 Action Bar Packet Overhead (20 Hz)
- **Location:** `SpeedrunTimerService.java`
- **Risk Level:** **LOW**
- **Issue:** Updates player action bars every single tick (20 Hz).
- **Mitigation:** Throttled action bar updates or scheduled periodic sync ticks.

### 3.3 Raw Velocity Physics (Anti-Cheat False Positives)
- **Location:** `ChainService.java`
- **Risk Level:** **LOW**
- **Issue:** Applies direct velocity vectors to airborne players, which can trigger strict anti-cheat plugins.
- **Mitigation:** Apply anti-cheat velocity exemptions or fire `PlayerVelocityEvent`.

---

## 4. State Management Vulnerabilities

### 4.1 Missing Plugin Disable Task Cleanup
- **Location:** `LobbyService.java`
- **Risk Level:** **RESOLVED** (Formerly LOW)
- **Previous Issue:** Countdown timer initiated repeating tasks without a `shutdown()` hook to cancel them on plugin disable/reload.
- **Resolution:** Implemented `LobbyService.shutdown()` and registered task cleanup in `AyinChallenge.onDisable()`.

### 4.2 In-Memory Stats Loss
- **Location:** `RunStatsService.java`
- **Risk Level:** **LOW**
- **Issue:** Per-run stats (healing/damage) are kept in memory and lost if the server reloads mid-run.
- **Mitigation:** Periodically flush run stats or snapshot to `stats.yml`.

### 4.3 Tab-Completion Permission Leak
- **Location:** `AyinChallengeCommand.java`
- **Risk Level:** **RESOLVED** (Formerly LOW)
- **Previous Issue:** Unfiltered subcommands returned to non-admin players in tab-completion.
- **Resolution:** Filtered command tab completions based on sender permissions (`ayinchallenge.admin`).
