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

- `common/` — `EnderChestStore` (core logic), `ConfigDir` (loader-agnostic path holder)
- `neoforge/` — `EnderChestPersistenceNeoForge` (@Mod), `EnderChestEvents` (@EventBusSubscriber)
- `fabric/` — `EnderChestPersistenceFabric` (ModInitializer + Fabric events), `GameModeChangeMixin`
- `forge/` — `EnderChestPersistenceForge` (@Mod + Forge events)

## Standards

Follows the same rules as all bh679 sibling mods:
- SemVer in `gradle.properties`; bump PATCH on every commit, MINOR on release
- Releases via `release.yml` dispatch only (no `push: tags` trigger)
- Gate workflow applies — see `~/.claude/CLAUDE.md` and `~/.claude/playbooks/`
