# SharedChain

SharedChain is a Paper 26.2 plugin for team-based challenge runs. All players share one health pool and are physically chained together. The team wins by killing the Ender Dragon; if any player dies, the whole team wipes and the run ends.

## Features

- **Shared Health**: one health pool for the entire team. Damage to any player is mirrored to everyone.
- **Sponsor Regeneration**: vanilla natural regen is disabled. A heartbeat task picks a single Sponsor player whose hunger and saturation pay for team healing (marked with a green heart), avoiding the multi-player food-drain multiplier bug.
- **Chained Together**: players are linked in random order by invisible anchor entities. A per-tick physics pass pulls the team back together when anyone strays past the chain's max distance.
- **Run Lifecycle**: lobby, countdown, run, with a speedrun timer, persistent death and attempt stats, and a configurable world reset on wipe.
- **World Reset**: the challenge runs in a dedicated fake overworld. On wipe, the plugin can unload, delete (or back up), and regenerate the overworld, nether, and end with a fresh seed.
- **Run Summary**: after a wipe, broadcasts the run's Support (most healing) and Victim (most damage taken).
- **Font Images**: drop PNGs into `plugins/SharedChain/font-images/` and render them anywhere with `%imagename%` placeholders (chat, broadcasts, tab list, action bar, the below-name death counter). The plugin generates, serves, and auto-applies the resource pack.

## Requirements

- Paper 26.2 (or a compatible fork)
- Java 25

## Commands

All commands are under `/sharedchain` (aliases: `/sc`, `/chain`).

| Command | Permission | Description |
|---|---|---|
| `/sharedchain start` | `sharedchain.admin` | Begin the run: starts the lobby countdown (the `[Start]` button does this too) |
| `/sharedchain reset` | `sharedchain.admin` | Regenerate the challenge world and return to the lobby (new attempt, run counter +1) |
| `/sharedchain stop` | `sharedchain.admin` | End the current run |
| `/sharedchain status` | `sharedchain.use` | Show run state, run #, participants, timers, and shared HP |
| `/sharedchain timer` | `sharedchain.use` | Show the current run time |
| `/sharedchain help` | `sharedchain.use` | Show all commands (also `/sharedchain` with no arguments) |
| `/sharedchain stats` | OP | Get/set run counter and per-player death counts |
| `/sharedchain fonts` | `sharedchain.use` | List loaded font images and the pack URL |
| `/sharedchain fonts reload` | OP | Re-scan `font-images/`, regenerate and re-apply the pack |
| `/sharedchain reload` | `sharedchain.admin` | Reload `config.yml` |

## Permissions

- `sharedchain.use`: basic status and timer commands (default: everyone)
- `sharedchain.admin`: start, stop, reset, reload (default: op; grants `use` and `debug`)
- `sharedchain.debug`: reserved for debug logging (default: op)

## Configuration

Everything lives in `config.yml`, organized by feature area:

- `shared-health`: max health, sync precision, totem and potion behavior
- `chain`: max distance, pull strength, leash refresh, anchor visuals
- `world-reset`: reset mode (`NONE`/`INTERNAL`/`FAHARE_COMPAT`), auto-reset on wipe, backups, challenge and holding world names
- `lobby`: border sizes and countdown length
- `timer`, `display`, `death-tracking`, `messages`: UI and messaging
- `font-images`: image folder, glyph base character, and pack server settings

## Font Images

SharedChain can render custom images in chat and plugin messages (tab header/footer, action bar, broadcasts) using `%name%` placeholders, ItemsAdder-style.

1. Create an image and save it as `plugins/SharedChain/font-images/<name>.png` (lowercase letters, numbers, `_` and `-` only).
2. The image **width and height must be multiples of 8** (e.g. 64x16, 128x16) and no larger than 256x256. The width is sliced into 8px-wide glyphs, so a 64px-wide image becomes 8 characters wide.
3. Reload with `/sharedchain fonts reload` (or restart the server). The plugin generates a resource pack, serves it from a built-in HTTP server (default port 8123), and pushes it to every player on join - accept the pack prompt in-game.
4. Type `%name%` in chat, or put it in any config message / tab header, and the image renders.

Optional per-image tweaks go in `plugins/SharedChain/font-images.yml`:

```yaml
mylogo:
  ascent: 10        # distance from the text baseline up to the image top (default: image height)
  width: 128        # resize before slicing into glyphs (multiples of 8, max 256)
  height: 48        # omit one to keep the aspect ratio
  render-height: 64 # render the (high-res) image at a smaller on-screen size - the
                    # client downsamples it, so it stays smooth instead of pixelated
```

`render-height` is the trick for logos: keep a high-resolution source image (e.g. 256x104) but render it compact (e.g. 64px tall) - much sharper than a native low-res image at the same size.

Key settings in `config.yml`:

- `font-images.base-char`: first glyph character (private use area, default `\uF000`).
- `font-images.pack-server.port`: HTTP port for the pack (default 8123).
- `font-images.pack-server.url`: override the pack download URL - set this if clients connect over LAN instead of localhost.

Glyphs are merged into the default font (the vanilla font's own providers are preserved), because chat always renders with the default font and ignores custom font attributes - this is also how ItemsAdder does it. The server pack's font providers take precedence, so other resource packs that also redefine the default font may be overridden. Players without the pack see the image as missing-glyph boxes.

The generated pack also bundles the **attempt counter** visuals (the run-number boss bar with the pill background and transparent bar), which previously required a separate custom resource pack. The plugin's boss bar already emits those glyphs, so with the pack applied everyone sees the counter. See `font-images.attempt-counter.enabled` and `.hide-bar` in `config.yml`.

## Tab List

Players can change their own tab-list/name-tag color with `/sharedchain color <color>` (admin: `/sharedchain color <player> <color>`). Colors persist in `stats.yml`. The current sponsor is always shown first in green with a heart icon.

The tab list header shows the logo image centered at the top (set via `display.tab-logo`, the name of a font image, e.g. `title`) with the `display.tab-header` title underneath. `display.tab-logo-padding` controls how many empty lines sit above the logo - the client anchors the header to the top of the screen, so tall images clip unless pushed down. It defaults to `-1`, which computes the padding automatically from the logo's height, so you can change the image size freely. The footer shows the run number and status, the current and total run timers, and the current run's top healer/sponge. Player names are colored by team - the current sponsor (green, with a heart icon), run participants (gold), and lobby-only players (gray). The whole tab list updates live in the lobby too, not just during a run.

Each player's death count renders under their nametag (the scoreboard's below-name slot) as `Deaths <icon>: <count>`. The icon is the font image set by `death-tracking.death-icon` (default `deaths`), so dropping `deaths.png` into `plugins/SharedChain/font-images/` and running `/sharedchain fonts reload` adds it; without a loaded icon the counter shows as plain `Deaths: <count>`. Set `death-tracking.show-death-counts` to `false` to hide the counter.

## World Reset

The challenge runs in a dedicated world (default `ayin_run`), so the primary server world is never touched:

- **INTERNAL**: the plugin unloads, deletes (or backs up with `backup-before-reset: true`), and regenerates the world in-process with a new seed.
- **FAHARE_COMPAT**: hands the reset to an external Fahare-compatible plugin via `fahare-reset-command`.
- **NONE**: no automatic reset; the world must be reset manually.

If the challenge previously ran in `world`, rename the world folder to the configured `challenge-world-name` (server stopped) or change the config. The primary server world cannot be used for in-process resets.

## Build

```bash
./gradlew build
```

Produces `build/libs/SharedChain-1.0.0.jar`. To launch a local test server:

```bash
./gradlew runServer
```

## Releases

GitHub Actions handles CI and releases. The `Build` workflow runs `./gradlew build` on every push to master and every pull request.

The `Release` workflow builds and publishes a release whenever a version tag is pushed. To cut a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Tags must follow the `v<major>.<minor>.<patch>` form. The workflow strips the `v` prefix, builds with that version (so the jar name and `plugin.yml` version match the tag), and attaches the jar to a GitHub Release with auto-generated notes.
