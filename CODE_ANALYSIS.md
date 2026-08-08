# AyinChallenge: Code Analysis & Cleanup Report

This document outlines "technical debt," redundant code, and potential performance improvements discovered during a full codebase audit.

## 1. Dead & Obsolete Code
These components are no longer functional but still occupy space in the project:

*   **`SharedFoodService.java` & `SharedFoodListener.java`**:
    *   *Status*: Since team-wide food syncing was disabled to fix hunger bugs, these classes are now essentially empty shells with commented-out or "Intentionally disabled" methods.
    *   *Action*: Remove these files and their registrations in `AyinChallenge.java` to clean up the `food` package.
*   **`SharedState.java` Redundant Fields**:
    *   *Fields*: `foodLevel`, `saturation`, `exhaustion`.
    *   *Status*: These fields were used for the old food syncing logic. They are now dead weight.
    *   *Action*: Remove these fields and their getters/setters.
*   **`config.yml` - `shared-food` Section**:
    *   *Status*: Options like `shared-saturation`, `starvation-enabled`, and `corrective-sync-interval` are currently ignored by the plugin logic.
    *   *Action*: Remove the `shared-food` section or move relevant "Sponsor" settings to `shared-health`.

## 2. Redundancy & Consolidation
Areas where logic is duplicated or split unnecessarily:

*   **UI Management Split**:
    *   *Issue*: `DisplayService` and `TabDisplayService` both manage player UI elements. They both have `updateAll()` methods called frequently.
    *   *Improvement*: Consolidate these into a single `UserInterfaceService`. This would reduce the number of separate tasks running and make it easier to manage how BossBars, Tab, and Scoreboards interact.
*   **Lobby vs. Run Management**:
    *   *Issue*: Both `LobbyService` and `RunManager` touch world borders and gamerules. 
    *   *Improvement*: Move all world-state changes (border size, gamerules, time locking) into `RunManager` or a dedicated `WorldStateService`. `LobbyService` should focus purely on the countdown and player coordination.

## 3. Potential Performance Issues
Optimization opportunities for smoother gameplay:

*   **Chain Physics Frequency**:
    *   *Issue*: The `ChainService` physics task runs every single tick (20 TPS). While necessary for smooth leashes, it performs distance calculations for every link in the chain.
    *   *Improvement*: Use `distanceSquared` instead of `distance` where possible to avoid expensive square root calculations in the main heartbeat loop.
*   **BossBar Player Iteration**:
    *   *Issue*: `DisplayService.refreshAttemptBossBar()` iterates through all online players and checks `contains()` on the BossBar's player list every update.
    *   *Improvement*: Only update the BossBar when a player joins/leaves the server or when the run state changes, rather than every time the Action Bar updates.

## 4. Technical Debt (API Usage)
*   **Deprecated GameRules**:
    *   *Issue*: `NATURAL_REGENERATION` in `GameRule` is marked as deprecated in newer Paper versions.
    *   *Action*: Transition to the newer namespaced keys or verify the recommended Paper alternative to future-proof the plugin for 1.21.2+.
*   **Inventory Drops**:
    *   *Issue*: Manual inventory dropping in `AyinChallenge.java` (`dropPlayerInventoryLikeVanilla`) is a complex workaround for skipping real deaths.
    *   *Action*: Consider letting the player actually "die" once and then quickly catching them, or using Paper's newer Inventory API for safer item handling.

## 5. Summary of Recommended Cleanup Path
1.  **Surgical Removal**: Delete `SharedFoodService`, `SharedFoodListener`, and redundant `SharedState` fields.
2.  **Config Cleanup**: Prune the `shared-food` section from `config.yml`.
3.  **UI Merge**: Combine the two display services into one for better maintainability.
4.  **Math Optimization**: Switch to squared distances in the chain logic.
