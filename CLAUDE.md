# Product Engineer — Ender Chest Persistence

Standalone NeoForge (+ Fabric/Forge) sibling mod that persists the Ender Chest across world
resets, with per-game-mode isolation. Extracted from Dungeon Train (bh679/dungeon-train-mc).

## Quick Reference

- **Mod ID:** `enderchestpersistence`
- **Group:** `games.brennan.enderchestpersistence`
- **Version file:** `gradle.properties` → `mod_version`
- **Build:** `./gradlew build` (all loaders)
- **Key output:** `neoforge/build/libs/enderchestpersistence-neoforge-<v>.jar`
- **Release:** `gh workflow run release.yml -f tag=v<version>`

## Structure

- `common/` — `EnderChestStore` (core logic), `ConfigDir` (loader-agnostic path holder),
  `StoreMode`/`EcpConfig`/`StoreLocation`/`AppDataDir`/`StoreSeeder` (where the store lives),
  `StoreFile`/`WriteGuard` (the .bak + never-overwrite-unreadable safety net)
- `neoforge/` — `EnderChestPersistenceNeoForge` (@Mod), `EnderChestEvents` (@EventBusSubscriber)
- `fabric/` — `EnderChestPersistenceFabric` (ModInitializer + Fabric events), `GameModeChangeMixin`
- `forge/` — `EnderChestPersistenceForge` (@Mod + Forge events)

## Store location (`store-location`, since 0.3.0)

`<config>/enderchestpersistence.properties` — a plain properties file, not a loader config API,
because `ModConfigSpec` does not exist on Fabric and this mod ships for all three loaders.

| Mode | Directory | Notes |
|---|---|---|
| `outside` (default) | OS app-data dir (`AppDataDir`) | Survives changing launcher or rebuilding an instance |
| `inside` | `<instance>/config/enderchestpersistence/` | The pre-0.3.0 path, byte-identical |
| `off` | — | Vanilla per-world chest; every `EnderChestStore` entry point no-ops |

Three things about this are load-bearing and easy to break:

1. **Dedicated servers always resolve to `inside`**, whatever the config says. A machine-level store
   would be shared by every unrelated DT server on the host.
2. **`StoreSeeder` copies an existing instance-local file into the outside store on first read**, and
   only when the destination is absent. Without it, shipping `outside` as the default would empty
   every existing player's chest — the exact bug the mode was added to fix. It copies rather than
   moves, so `inside` still works after a switch back.
3. **`off` disables the slot-key swapping**, so a host mod's chest isolation (Dungeon Train's Free
   Play lock and its per-difficulty stash) stops isolating. Called out in the config comments.

Everything that varies by platform is a parameter (`AppDataDir.resolve`, `StoreLocation.resolve`),
so all three OS branches and the fallbacks are unit-tested wherever the tests happen to run.

## Safety net around the file (since 0.4.0)

All reads and writes of `<uuid>.dat` go through `StoreFile`; `EnderChestStore` never touches
`NbtIo` directly. Two guarantees, both zero-config, both unit-tested over real files:

1. **`<uuid>.dat.bak` is the last version that held any items.** Rotated on write only when the file
   being replaced has ≥ 1 stack in any slot, so a loss followed by empty sessions never displaces it.
   A player recovers by renaming it to `.dat` with the game closed (the default config says so).
2. **An unreadable file is never written over.** `StoreFile.read` retries, then falls back to the
   `.bak` (main set aside as `.dat.corrupt-<stamp>`, `.bak` copied back). With no usable `.bak` it
   reports `UNREADABLE`, `EnderChestStore` puts the UUID in `WriteGuard`, and every write path
   (`save`, `applySlot`, `flush`, `flushAll`) refuses with an ERROR until logout clears it.

The guard is deliberately narrow: a player who empties their own chest still saves as empty.
Refusing that would hand the items back on relog — a dupe. `restore()` logs its outcome at INFO on
every login so an "empty chest" report can be diagnosed from `latest.log`.

## Standards

Follows the same rules as all bh679 sibling mods:
- SemVer in `gradle.properties`; bump PATCH on every commit, MINOR on release
- Releases via `release.yml` dispatch only (no `push: tags` trigger)
- Gate workflow applies — see `~/.claude/CLAUDE.md` and `~/.claude/playbooks/`
