# Stabilization Execution Blueprint

## Phase 1: Parallel Subagent Execution

- **Subagent 1 (Gradle & API Auditor):** Normalize `build.gradle.kts` dependencies, java toolchain, and `src/main/resources/plugin.yml` for Paper 26.2 and JDK 25.0.3. Verify the compile targets are unambiguous and aligned with `docs/`.
- **Subagent 2 (Reset & Threading Optimizer):** Refactor `WorldLifecycleService.java` and `ResetCoordinator.java`. Remove `Thread.sleep` from `deleteDirectory`. Move disk deletion to an async thread using `CompletableFuture.runAsync()`, returning to the region scheduler (via `runDelayed` or `run`) for the world creation steps. Ensure `isTickingWorlds` checks and `unloadWorld` remain on the main thread.
- **Subagent 3 (Persistence & Cleanup Auditor):** Refactor `AyinChallenge.saveStats()`, `RunManager.java`, and `DeathTrackerService.java` to perform asynchronous atomic `.tmp` saves for `stats.yml`. Address blocking Mojang API calls in `AyinChallengeCommand.java` by replacing string-based `getOfflinePlayer` with async lookups. Add `LobbyService.shutdown()` and hook it into `AyinChallenge.onDisable()`.
- **Subagent 4 (Modernization Updater):** Replace deprecated string-based `GameRule` usages in `RunManager.java` and `RunListener.java` with Paper typed GameRule getters/setters. Replace legacy `KeyedBossBar` in `UserInterfaceService.java` with Adventure API `BossBar`. Update any code fallbacks to match `config.yml` defaults.
- **Subagent 5 (Docs Alignment):** Review the changes applied by agents 1-4 and update `docs/risk-register.md`, `docs/codebase-map.md`, and `docs/config-reference.md` to indicate the mitigated hotspots, new API usages, and config fixes.

## Guidelines
- Strict preservation of all gameplay mechanics.
- No `PlayerMoveEvent` logic changes.
- Return to main thread via `GlobalRegionScheduler` or Bukkit scheduler after async IO.
