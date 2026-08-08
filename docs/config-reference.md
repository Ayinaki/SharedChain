# Configuration Reference Guide (Post-Stabilization)

This document details all configuration keys, YAML files, default values, fallbacks, runtime behaviors, and persistence mechanics in **AyinChallenge**.

---

## YAML Configuration Files

| File | Location | Description | Persistence Strategy |
|---|---|---|---|
| `config.yml` | Plugin Data Folder | Primary configuration file containing gameplay, timer, display, world reset, chain physics, and message settings. | Read-only at runtime; reloaded via `/ac reload`. |
| `plugin.yml` | `src/main/resources/plugin.yml` | Plugin manifest file defining main class, permissions, command aliases, and Bukkit API version (Paper 26.2). | Static build artifact. |
| `stats.yml` | Plugin Data Folder | Persistent data store tracking run counter, run state, player death counts, total elapsed speedrun time, and damage/heal stats. | Asynchronous atomic write (`stats.yml.tmp`) & replace (`REPLACE_EXISTING`). |

---

## Configuration Key Reference (`config.yml`)

### 1. `run` Section
- **`run.enabled-worlds`** *(List of String, Default: `[world_nether, world_the_end]`)*: List of secondary world names where challenge rules apply. The main challenge world (`fakeOverworld`) is always enabled.
- **`run.auto-join`** *(Boolean, Default: `true`)*: Automatically registers players as active run participants when they join an enabled world.
- **`run.persist-state`** *(Boolean, Default: `false`)*: Controls whether the active run state persists across server restarts.

### 2. `shared-health` Section
- **`shared-health.max-health`** *(Double, Default: `20.0`)*: Team max health attribute value applied to all participants.
- **`shared-health.precision`** *(Double, Default: `0.001`)*: Floating-point delta threshold required before triggering health attribute synchronization.
- **`shared-health.totem-save-all`** *(Boolean, Default: `true`)*: Controls whether a single participant triggering a Totem of Undying prevents a team wipe and synchronizes saved health across all team members.
- **`shared-health.instant-potions-shared`** *(Boolean, Default: `true`)*: Flag indicating instant health/damage potion sharing behavior.

### 3. `timer` Section
- **`timer.display-mode`** *(String, Default: `"ACTION_BAR"`)*: Display destination for the run timer (`ACTION_BAR`).
- **`timer.format`** *(String, Default: `"HH:mm:ss.SS"`)*: `SimpleDateFormat` pattern string used for speedrun time formatting.

### 4. `display` Section
- **`display.tab-enabled`** *(Boolean, Default: `true`)*: Enables Adventure-based tab list header and footer formatting.
- **`display.scoreboard-enabled`** *(Boolean, Default: `true`)*: Enables custom scoreboard team assignments and death objective display on the tab list.
- **`display.tab-header`** *(String, Default: `"<gold><b>AyinChallenge</b></gold>"`)*: MiniMessage formatted header text rendered on the tab list.

### 5. `death-tracking` Section
- **`death-tracking.show-death-counts`** *(Boolean, Default: `true`)*: Controls rendering of death counts in tab list objectives.
- **`death-tracking.dramatic-wipe`** *(Boolean, Default: `true`)*: Enables audio playback when a run wipes.
- **`death-tracking.wipe-sound`** *(String, Default: `"entity.illusioner.prepare_blindness"`)*: Adventure sound key played globally upon team wipe.
- **`death-tracking.wipe-sound-pitch`** *(Double, Default: `1.0`)*: Pitch multiplier for wipe sound playback.

### 6. `world-reset` Section
- **`world-reset.mode`** *(String, Default: `"INTERNAL"`)*: World reset execution strategy (`INTERNAL`, `FAHARE_COMPAT`, or `NONE`).
- **`world-reset.fahare-reset-command`** *(String, Default: `"fahare reset"`)*: Console command executed when reset mode is `FAHARE_COMPAT`.
- **`world-reset.auto-reset-on-wipe`** *(Boolean, Default: `true`)*: Automatically triggers world reset when team wipe occurs.
- **`world-reset.backup-before-reset`** *(Boolean, Default: `false`)*: Moves world directory to `ayinchallenge-backups/` before deletion in `INTERNAL` mode.
- **`world-reset.challenge-world-name`** *(String, Default: `"ayin_run"`)*: Identifier for the active challenge world.
- **`world-reset.holding-world-name`** *(String, Default: `"ayin_limbo"`)*: Identifier for the limbo holding world used during reset.
- **`world-reset.save-on-unload`** *(Boolean, Default: `false`)*: Saves world chunks during unload phase before deletion.

### 7. `lobby` Section
- **`lobby.lobby-border-size`** *(Double, Default: `10.0`)*: Spawn world border size prior to run start.
- **`lobby.active-border-size`** *(Double, Default: `100000.0`)*: World border size set when the run starts.
- **`lobby.countdown-seconds`** *(Integer, Default: `5`)*: Countdown timer duration (in seconds) prior to starting the run.

### 8. `chain` Section
- **`chain.enabled`** *(Boolean, Default: `true`)*: Enables player chain tethering mechanics.
- **`chain.anchor-scale`** *(Double, Default: `0.1`)*: Scale attribute applied to invisible bat anchor entities.
- **`chain.anchor-back-offset`** *(Double, Default: `0.38`)*: Backward offset (in blocks) relative to player yaw.
- **`chain.anchor-side-offset`** *(Double, Default: `0.0`)*: Lateral offset (in blocks) relative to player yaw.
- **`chain.anchor-y-offset`** *(Double, Default: `0.7`)*: Vertical offset (in blocks) from player feet to waist level.
- **`chain.max-distance`** *(Double, Default: `8.0`)*: Maximum allowed separation distance before spring physics pull forces engage.
- **`chain.slack-distance`** *(Double, Default: `0.75`)*: Additional distance tolerance before pull physics engage.
- **`chain.pull-strength`** *(Double, Default: `0.08`)*: Acceleration multiplier per tick applied to tethered players.
- **`chain.max-pull-per-tick`** *(Double, Default: `0.18`)*: Cap on velocity vector addition per tick.
- **`chain.leash-refresh-interval`** *(Integer, Default: `100`)*: Frequency (in ticks) to force re-verification of leash connections.
- **`chain.max-result-velocity`** *(Double, Default: `1.6`)*: Maximum magnitude threshold for player velocity vectors.
- **`chain.anchor-prediction-ticks`** *(Double, Default: `0.35`)*: Motion prediction multiplier (in ticks) applied to anchor entity position.

### 9. `messages` Section
- **`messages.prefix`** *(String, Default: `"<dark_gray>[<gold>AyinChallenge</gold>]</dark_gray> "`)*: Global MiniMessage prefix.
- **`messages.run-started`** *(String, Default: `"<green>The challenge has started! Good luck!</green>"`)*
- **`messages.run-stopped`** *(String, Default: `"<red>The challenge has been stopped.</red>"`)*
- **`messages.run-finished`** *(String, Default: `"<gold><b>Congratulations!</b> The challenge has been completed in <white><timer></white>!</gold>"`)*
- **`messages.run-wiped`** *(String, Default: `"<red><b>WIPE!</b> <player> died due to <cause>. Total deaths: <deaths>.</red>"`)*
- **`messages.no-permission`** *(String, Default: `"<red>You don't have permission to do that.</red>"`)*

---

## Code Fallback Alignment Summary

All code fallbacks in `AyinChallenge` services match the default `config.yml` values:

| Key | Code Reference | Default / Fallback Value | Status |
|---|---|---|---|
| `world-reset.fahare-reset-command` | `WorldResetService.java` | `"fahare reset"` | Aligned |
| `chain.leash-refresh-interval` | `ChainService.java` | `100` | Aligned |
| `chain.max-result-velocity` | `ChainService.java` | `1.6` | Aligned |
| `chain.anchor-prediction-ticks` | `ChainService.java` | `0.35` | Aligned |
