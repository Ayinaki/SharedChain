# Fahare

Fahare (short for **Fa**st **Ha**rdcore **Re**set) is a Minecraft: Java Edition multiplayer server mod that
automatically resets your hardcore world when all online players die. It is currently available for Paper 1.19.3+.

By default, the mod is configured to a fairly normal hardcore experience,
though note that for the full experience you should change `difficulty` in your `server.properties` to `hard`.
This will trigger the mod to enable hardcore hearts and grants a proper hardcore experience.

## Configuration

| Setting      | Default | Description                                                                                                                                                                          |
|--------------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `backup`     | `true`  | Whether to save and backup worlds before resetting.<br>When disabled, worlds will be permanently deleted upon reset (much faster).                                                   |
| `auto-reset` | `true`  | Whether resets should happen automatically upon the death of all players.<br>When disabled, you may instead run `/fahare reset` to manually reset the world.                         |
| `any-death`  | `false` | Whether to trigger a reset upon *any* death.<br>When disabled, automatic resets will trigger only when all players are dead.<br>This setting is ignored if `auto-reset` is disabled. |
| `lives`      | `1`     | How many lives a player should have before they are considered dead.                                                                                                                 |

## Commands

| Command         | Permission     | Description               |
|-----------------|----------------|---------------------------|
| `/fahare reset` | `fahare.reset` | Manually reset the world. |
