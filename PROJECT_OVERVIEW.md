# AyinChallenge Project Overview

AyinChallenge is a Minecraft (Paper/Bukkit) plugin designed for team-based challenges. It features shared resources, a physical "chain" connecting players, and an automated world reset system.

## Core Features

### 1. Shared Health & Food
- **Shared Health**: All participants share a single health pool. Damage taken by one player is reflected in everyone's health bar.
- **Heartbeat Regeneration**: Natural regeneration is managed by a custom heartbeat task (0.5s for Fast Regen, 4.0s for Slow Regen). It selects a "Sponsor" player to pay the food cost, preventing the "multiplier effect" where multiple players drain food for a single heal.
- **Regen Sponsor Indicator**: A green heart (`❤`) appears next to a player's name (above their head and in the tablist) when they are currently acting as the Sponsor for the team's regeneration. The indicator lingers for 2 seconds after the healing event to provide clear visual feedback.
- **Shared Food**: *Note: Global food level syncing is currently disabled.* Players maintain individual food bars, but their saturation/hunger is used to power the shared health regeneration via the Sponsor logic. Hunger and saturation are fully locked during non-running states.

### 2. Chained Together
- **Physical Link**: Players are linked in a random order using invisible "anchor" entities (Bats) and leashes.
- **Physics Engine**: Custom logic pulls players together if they exceed a configurable maximum distance (`max-distance`).
- **Visuals**: Provides a constant visual reminder of the team's connection.

### 3. World Reset System
- **Fake Overworld**: To protect the server's main overworld, the challenge takes place in a "fake" overworld.
- **Automated Wipes**: When a player dies (resulting in a team wipe), the plugin can automatically reset the challenge worlds.
- **Reset Coordination**: Unloads, deletes, and recreates worlds (Overworld, Nether, End) with a fresh seed. Progress is logged to the console to maintain a clean chat environment.
- **Limbo World**: Players are moved to a temporary "limbo" world during the reset process.

### 4. Run Management
- **States**: Manages the challenge lifecycle through states: `LOBBY`, `STARTING`, `RUNNING`, `FINISHED`, `WIPED`, and `RESETTING`.
- **Lobby Protection**: During non-running states, players are immune to all damage (fall, lava, PVP) and their food/saturation levels are locked.
- **Center Player Announcement**: In the lobby phase, the plugin identifies and broadcasts the "center" player of the chain order to help the team coordinate.
- **Dramatic Wipe**: When enabled in config, a team wipe triggers a configurable sound effect (supports vanilla and resource pack keys) to highlight the "Final Moment."
- **Clean Death Messages**: Vanilla death messages are suppressed during a wipe; only the custom, formatted AyinChallenge wipe message is broadcast.
- **Accurate Tracking**: Death counts in the tablist are strictly controlled by the plugin to ensure they match the persistent stats and broadcast data.
- **Post-Run Summary**: After a team wipe, the plugin broadcasts a summary highlighting the "Support" (most healing provided) and "Victim" (most damage taken) for that specific run.
- **Timer**: Tracks the duration of the run with high precision.
- **Stats Tracking**: Persists death counts and run attempts in `stats.yml`.

## Technical Architecture

### Services
- `AyinChallenge`: Main plugin class, initializes and coordinates all services.
- `RunManager`: Handles the lifecycle of a challenge run.
- `SharedHealthService`: Manages the synchronization of team health and sponsor-based regeneration.
- `ChainService`: Implements the leash visuals and optimized pulling physics.
- `WorldResetService`: Coordinates world deletion and recreation.
- `UserInterfaceService`: Consolidated service managing all player UI (BossBars, Scoreboards, and Tab headers/footers).
- `LobbyService`: Manages the pre-run lobby state and world borders.

### Listeners
- `DeathListener`: Handles player deaths and triggers wipes.
- `RunListener`: Manages player joins, quits, and interactions relative to the run state.
- `SharedHealthListener`: Intercepts vanilla events to synchronize health.
- `RedirectionListener`: Handles world-specific event redirection (e.g., portals).

## Configuration
- `config.yml`: Comprehensive settings for all features, including health precision, chain strength, reset modes, and UI customization.
- `plugin.yml`: Defines commands (`/ayinchallenge`) and permissions.

## Commands
- `/ayinchallenge (ac)`:
    - `reset`: Manually triggers a world reset.
    - `start`: Starts the challenge from the lobby.
    - `stop`: Stops the current run.
    - `lobby`: Returns players to the lobby.
    - `info`: Displays current run information.

## Current State
The project is a functional prototype with robust implementations of its core mechanics. It utilizes the Adventure API for modern text formatting and the Paper API for efficient world and entity management.
