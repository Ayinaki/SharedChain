# Feature Behavior Map

This document details the functional gameplay mechanics, player interaction rules, and behavioral specifications implemented in the AyinChallenge plugin.

---

## 1. Chain Mechanics

### 1.1 Chain Formation & Ordering
- **Single-File Sequence:** When a run or lobby session starts, all eligible online players in enabled worlds are grouped into a randomly shuffled chain order (`ChainService.activateFor`).
- **Center Player Announcement:** The plugin calculates the median index player (`chainOrder.get(chainOrder.size() / 2)`) and broadcasts their name as the team's anchor center upon lobby setup.
- **Cross-Dimension Handling:** If two adjacent players in the chain sequence enter different dimensions (e.g., Overworld vs. Nether), the leash tether between them is automatically detached (`currentAnchor.setLeashHolder(null)`) until they share the same world again.

### 1.2 Leash Visualization & Anchoring
- **Invisible Bat Anchors:** Invisible, invulnerable, silent, gravity-free, and collision-disabled `Bat` entities (`CHAIN_ANCHOR_TAG`) are spawned at each player's waist location.
- **Velocity Prediction:** Waist anchor locations are offset dynamically using forward velocity prediction (`chain.anchor-prediction-ticks`, default `0.35`) and backward offset (`chain.anchor-back-offset`, default `0.38`) to provide smooth visual leash rendering during movement.
- **Periodic Refresh:** Leashes are refreshed every 100 ticks (configurable via `chain.leash-refresh-interval`) to prevent vanilla Bukkit leash snapping/detachment bugs.
- **Entity Interaction Protection:** All damage, right-click interaction, and unleash events targeting chain anchor bats are strictly cancelled (`RunListener.onChainAnchor*`).

### 1.3 Tether Physics & Elastic Pull
- **Distance Threshold:** Players are linked with a maximum tether distance (`chain.max-distance`, default `8.0` blocks) plus slack (`chain.slack-distance`, default `0.0`).
- **Pull Force Application:** Every tick (20 Hz), if the distance between adjacent chained players exceeds the threshold:
  - An equal and opposite pulling velocity is applied to both players towards each other.
  - Pull acceleration is calculated as `pullAmount = Math.min((distance - maxDistance) * pullStrength, maxPullPerTick)` (`pullStrength` = 0.08, `maxPullPerTick` = 0.5).
  - Total velocity per tick is capped by `chain.max-result-velocity` (default `1.6`) to prevent extreme acceleration or physics explosions.

---

## 2. Shared Health & Custom Regeneration System

### 2.1 Shared Team Health Pool
- **Unified Health State:** All participating players share a single synchronized team health pool (`SharedState`, default max HP = `20.0`).
- **Damage Mirroring:** When any player takes damage, the vanilla damage pipeline executes normally (playing hit sounds, knockback, and I-frames). On the following server tick, `SharedHealthListener` queries `syncFromPlayerHealth()`, updates the global `SharedState`, broadcasts damage taken to chat (e.g., `[A] Player has taken 1.5 ❤ damage.`), and clamps all team members' health to the exact same value.

### 2.2 Custom Food & Heartbeat Regeneration
- **Vanilla Natural Regen Disabled:** `GameRule.NATURAL_REGENERATION` is set to `false` in all enabled worlds to eliminate vanilla food exhaustion bugs. Vanilla satiated/regen events are cancelled in `SharedHealthListener`.
- **Heartbeat Task:** A periodic task runs every 10 ticks (0.5 seconds, matching vanilla fast regen frequency):
  1. **Fast Regeneration (Hunger 20 + Saturation > 0):** Selects the player with the highest saturation level as the "Fast Sponsor". Restores `1.0` HP (0.5 heart) to the shared pool and adds `6.0` exhaustion points to the sponsor.
  2. **Slow Regeneration (Hunger >= 18):** If no fast sponsor exists and 80 ticks (4 seconds) have elapsed, selects the player with the highest combined food and saturation level as the "Slow Sponsor". Restores `1.0` HP to the shared pool and adds `6.0` exhaustion points to the sponsor.
- **Regen Sponsor Visual:** The active regen sponsor is highlighted in tab list/scoreboard with a green heart prefix (`❤ `) for 40 ticks (2 seconds).
- **External Healing Sources:** Golden Apples, Health Potions, and Instant Health effects bypass the heartbeat task and immediately sync health to the shared team pool on the next tick.
- **Totem of Undying:** If enabled (`shared-health.totem-save-all: true`), a single totem activation by any player syncs restored health across the entire team.

---

## 3. Run Lifecycle & World Management

### 3.1 Pre-Run Lobby Phase (`RunState.IDLE`, `STARTING`)
- **Restricted Lobby Border:** World border size is shrunk to `10.0` blocks around world spawn (`lobby.lobby-border-size`).
- **Time Lock:** World time is locked to `0` (daylight) every 10 ticks while idle.
- **Interaction & Combat Lock:** Block break, block place, entity interact, PVP, fall/environmental damage, hunger loss, and exhaustion are completely cancelled for all players in enabled worlds.
- **Auto-Join:** Players joining or switching to an enabled world automatically register as active run participants.

### 3.2 Run Start Sequence
- **Triggering Reset:** Admin executes `/ayinchallenge start` or `/ayinchallenge reset`.
- **Limbo Transfer:** Players are set to Spectator mode and teleported to a void flat world ("limbo").
- **World Re-creation:** Overworld, Nether, and End worlds are unloaded, deleted on disk, and re-created using a newly generated shared seed (`ResetCoordinator`).
- **Lobby Setup & Countdown:** Players are teleported to the new overworld spawn. Clicking `[Start]` or typing `/ayinchallenge startconfirm` initiates a 5-second title countdown with note block sound cues.
- **Active Transition:** At countdown zero:
  - World border expands to active size (`100,000` blocks).
  - Hardcore mode (`world.setHardcore(true)`) and Immediate Respawn (`DO_IMMEDIATE_RESPAWN = true`) are enforced.
  - Speedrun timer starts ticking, action bar timer activates, and team health is initialized to max.

### 3.3 Run End & Wipe Handling
- **Wipe Trigger (`RunState.RUNNING` -> `WIPED`):** If ANY participant dies (`PlayerDeathEvent`), vanilla death messages are suppressed.
- **Wipe Effects:**
  - Plays a dramatic illusioner blindness sound across the server.
  - Broadcasts wipe announcement including death cause and total player wipes.
  - Displays post-run performance summary (Top Sponsor / Top Damage Victim).
  - All participants' items drop naturally at their current location (if `keepInventory` is false), and players transition to Spectator mode.
  - OP players receive a clickable prompt to trigger `/ayinchallenge reset`.
- **Victory Condition (`RunState.RUNNING` -> `FINISHED`):** `RunFinishDetector` listens for `EntityType.ENDER_DRAGON` death. After a 10-second (200 ticks) delay for dragon death animation, the run finishes, timer stops, and victory completion time is broadcast.

---

## 4. Redirection & World Mapping
- **Fake Overworld Isolation:** The primary server overworld (`minecraft:overworld`) is treated as a fallback/lobby container. All challenge gameplay occurs in a custom fake overworld instance (`AyinChallenge.REAL_OVERWORLD_KEY` vs fake overworld key).
- **Portal & Respawn Redirection:** `RedirectionListener` intercepts nether/end portals, player joins, and player respawns heading to `minecraft:overworld`, redirecting players to the current fake overworld spawn location.
