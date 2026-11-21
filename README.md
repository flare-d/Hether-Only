# Nether World

**Short description (English):** Adds a Nether-focused world preset with safe spawning, Nether strongholds, and a path to the End—while keeping vanilla world presets available.

A Fabric mod for **Minecraft Java Edition 26.3**.

Release **1.0.5** fixes Nether stronghold vertical placement. The custom Nether dimension now uses a copy of the Minecraft 26.3 Nether noise settings with `sea_level: 63`, while retaining the vanilla Nether noise router, material rule, and 0–128 generation bounds. This is required because vanilla `StrongholdStructure` builds its pieces at a fixed Y and then moves the complete layout below the generator sea level; the vanilla Nether value of 32 pushes too much of the structure into the bottom of the dimension.

## Features

- Adds a separate **Nether World** preset to world creation; it does not replace vanilla presets.
- Keeps the technical Overworld and End dimensions registered for Minecraft compatibility; the Overworld is not playable in this preset.
- Places first-time players and respawning players at a protected Nether spawn point.
- Rebuilds the obsidian spawn platform when the player respawns or returns from the End.
- Enables vanilla `minecraft:stronghold` generation in Nether biomes for this preset; Eyes of Ender and `/locate structure minecraft:stronghold` use the vanilla structure identity.
- Awards the vanilla **Eye Spy** advancement when either a vanilla or Nether stronghold is discovered.
- Keeps vanilla strongholds in the Eye of Ender search tag while adding Nether strongholds.
- Extends both the stronghold-valid-biome tag and the stronghold ring preferred-biome tag, so ring positions resolve to actual Nether biome locations.
- Adds netherrack to vanilla stone material tags for stone-equivalent crafting recipes.
- Includes 14 language files.

## Requirements

- Minecraft Java Edition **26.3**
- Java Development Kit **25 or newer**
- Fabric Loader **0.20.1 or newer**
- Fabric API for Minecraft 26.3

The versions in `gradle.properties` are aligned with the Fabric development recommendations for Minecraft 26.3:

- Minecraft: `26.3`
- Fabric Loader: `0.20.1`
- Fabric Loom: `1.19-SNAPSHOT`
- Fabric API: `0.166.0+26.3`
- Gradle Wrapper: `9.8.2`

## Build the mod

Open a terminal in the project directory (the directory containing `gradlew`).

### Linux / macOS

```bash
java -version
chmod +x ./gradlew
./gradlew clean build
```

### Windows (PowerShell)

```powershell
java -version
.\gradlew.bat clean build
```

The finished mod will be created at:

```text
build/libs/nether-only-1.0.5.jar
```

`./gradlew check` also validates every bundled JSON resource and checks the 26.3 Eye Spy advancement schema before packaging.

If Gradle reports that it cannot find a Java 25 toolchain, install a JDK 25 distribution and make it the active Java version (`JAVA_HOME` must point to the JDK, not a JRE). Check with `java -version` and `./gradlew --version`.

## Install and play

1. Install Minecraft Java Edition **26.3** and launch it once.
2. Install Fabric Loader for **26.3** using the Fabric Installer.
3. Download the Fabric API build for **Minecraft 26.3**.
4. Copy `nether-only-1.0.5.jar` and the Fabric API JAR into the Minecraft `mods` folder.
5. Start the **Fabric 26.3** profile.
6. Create a **new world** and select **Nether World** in the world-preset selection.

Typical `mods` folder locations:

- Linux: `~/.minecraft/mods`
- Windows: `%appdata%\.minecraft\mods`
- macOS: `~/Library/Application Support/minecraft/mods`

For a custom launcher or game directory, use that instance's own `mods` folder. In multiplayer, install the mod and Fabric API on the server as well as on every client.

## Development testing

To launch a development client with the mod loaded:

```bash
./gradlew runClient
```

To launch a development server:

```bash
./gradlew runServer
```

## Version compatibility

This project is configured and source-targeted for **Minecraft 26.3**. Minecraft's `26.x` releases are not one interchangeable mod target: changes between releases can require separate dependency updates and code ports. Do not change only the `minecraft_version` string and assume the same JAR will work on every `26.x` release. For another release, update the Minecraft, Loader, Loom, Fabric API, and Gradle versions together, then compile and test against that exact version.

Minecraft 26.4 snapshots are pre-release builds and are not claimed as supported by this 26.3 build.

Existing worlds are not automatically converted. Back up saves before testing or updating mods.


## 1.0.5 changes

- Fixes the actual Nether stronghold generation height instead of only changing biome eligibility.
- Adds a dedicated `nether_only:nether` noise-settings entry based on the exact Minecraft 26.3 Nether settings. The Nether noise router, material rule, minimum Y, and generation height remain unchanged; `sea_level` is set to 63 so vanilla stronghold pieces are moved into the playable Nether instead of being clipped against Y=0.
- Points the Nether World preset at the dedicated noise settings.
- Adds build-time assertions for the custom noise settings and the preset reference.

## 1.0.4 changes

- Fixed compilation: resolve the MinecraftServer from the destination `ServerLevel` in `AFTER_PLAYER_CHANGE_LEVEL`.
- Preserves event-driven Overworld rejection without restoring per-tick player polling.

## 1.0.3 changes

- Adds all five Nether biomes to `minecraft:stronghold_biased_to` as well as the vanilla stronghold-valid-biome tag. This is the missing half of the vanilla concentric-ring algorithm: allowing the structure in a biome alone does not make the ring search choose positions there.
- Replaces per-tick Overworld polling with Fabric’s player-level-change event and corrects saved players on login.
- Caches the persistent spawn anchor per loaded level, removes duplicate platform rebuilds, avoids rewriting already-correct obsidian blocks, and reduces neighbor-update work.
- Caps both safe-spawn scans at 64 blocks, preventing large world-generation scans during first join.

## 1.0.2 changes

- Nether portals are disabled in the Nether World preset; any player forced into the technical Overworld dimension is returned to the Nether spawn.
- The Nether dimension uses a 1:1 coordinate scale, so dimension coordinate conversion no longer applies an unwanted 8x multiplier.
- Nether biomes are added to the vanilla stronghold biome tag. The vanilla `minecraft:stronghold` structure and vanilla concentric-ring placement are used, so `/locate structure minecraft:stronghold` and Eye of Ender use the same structure identity.
- The mod no longer overrides the vanilla Eye Spy advancement. It is awarded by Minecraft's normal stronghold-location trigger rather than by a modded criterion that could be satisfied at initial spawn.

The Overworld stem remains registered for Minecraft server bootstrap compatibility, but players cannot use it as a playable dimension in this preset. The biome-tag extension is data-driven and therefore also makes vanilla Nether biomes eligible for the vanilla stronghold structure in other worlds; vanilla Overworld and End generation are otherwise untouched.
