package com.example.netheronly;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;

public final class NetherOnly implements ModInitializer {
    public static final String MOD_ID = "nether_only";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int SEARCH_RADIUS = 64;
    private static final int SEARCH_STEP = 8;
    private static final int SEARCH_TOP_Y = 112; // Stay below the Nether roof bedrock layer.
    private static final int SEARCH_BOTTOM_Y = 32;
    private static final int PAD_RADIUS = 3;
    // Server-thread-only cache: avoid disk reads and repeated spawn searches.
    private static final Map<ServerLevel, BlockPos> SPAWN_CACHE = new WeakHashMap<>();

    @Override
    public void onInitialize() {
        // The Overworld remains registered for Minecraft's bootstrap, but any player
        // transferred there is immediately returned to the custom Nether spawn.
        // A level-change event avoids polling every online player on every server tick.
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            if (!destination.dimension().equals(Level.OVERWORLD)) {
                return;
            }
            ServerLevel nether = destination.getServer().getLevel(Level.NETHER);
            if (nether != null && isNetherWorld(nether)) {
                moveToNetherSpawn(player, nether);
            }
        });
        LOGGER.info("Nether World preset and Nether progression initialized; Overworld access is disabled.");
    }

    /** True only for the custom Nether dimension used by the Nether World preset. */
    public static boolean isNetherWorld(ServerLevel level) {
        if (!level.dimension().equals(Level.NETHER)) {
            return false;
        }
        return level.dimensionTypeRegistration().unwrapKey()
                .map(key -> key.identifier().getNamespace().equals(MOD_ID)
                        && key.identifier().getPath().equals("nether"))
                .orElse(false);
    }

    /**
     * Returns the persistent spawn position. The anchor is stored in the world's
     * own data directory, separately from vanilla's Overworld spawn coordinates.
     */
    public static BlockPos findSafeSpawn(ServerLevel nether) {
        BlockPos cached = SPAWN_CACHE.get(nether);
        if (isUsableSpawn(nether, cached)) {
            rebuildSpawnPad(nether, cached);
            return cached;
        }

        BlockPos saved = loadSpawnAnchor(nether);
        if (isUsableSpawn(nether, saved)) {
            SPAWN_CACHE.put(nether, saved);
            rebuildSpawnPad(nether, saved);
            return saved;
        }

        // Search in expanding rings. Require a broad safe patch, not merely one safe block,
        // so the player cannot immediately step into lava, fire or a one-block pit.
        // Do not use a heightmap here: in the Nether its highest surface is often the
        // bedrock ceiling (Y≈127), which would place a new player above the playable area.
        // Scan the playable lower half from top to bottom and reject lava/solid columns.
        int topY = Math.min(SEARCH_TOP_Y, nether.getMinY() + nether.getHeight() - 8);
        for (int radius = 0; radius <= SEARCH_RADIUS; radius += SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += SEARCH_STEP) {
                    if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int y = topY; y >= Math.max(SEARCH_BOTTOM_Y, nether.getMinY() + 8); y--) {
                        BlockPos candidate = new BlockPos(dx, y, dz);
                        if (isSafePatch(nether, candidate)) {
                            saveSpawnAnchor(nether, candidate);
                            rebuildSpawnPad(nether, candidate);
                            LOGGER.info("Selected safe Nether spawn at {}.", candidate);
                            return candidate;
                        }
                    }
                }
            }
        }

        // If a broad patch does not exist (for example, on a custom terrain mod),
        // fall back to a single genuinely safe column rather than choosing a lava top.
        for (int radius = 0; radius <= SEARCH_RADIUS; radius += SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += SEARCH_STEP) {
                    if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int y = topY; y >= Math.max(SEARCH_BOTTOM_Y, nether.getMinY() + 8); y--) {
                        BlockPos candidate = new BlockPos(dx, y, dz);
                        if (isSafeColumn(nether, candidate)) {
                            saveSpawnAnchor(nether, candidate);
                            rebuildSpawnPad(nether, candidate);
                            LOGGER.warn("Using a single safe Nether spawn column at {}.", candidate);
                            return candidate;
                        }
                    }
                }
            }
        }

        // Last resort for fully custom/hostile terrain: create a raised obsidian landing pad.
        // This is reached only when no naturally safe column exists in the search area.
        int y = Math.max(nether.getMinY() + 16,
                Math.min(64, nether.getMinY() + nether.getHeight() - 16));
        BlockPos emergency = new BlockPos(0, y, 0);
        saveSpawnAnchor(nether, emergency);
        rebuildSpawnPad(nether, emergency);
        LOGGER.warn("No natural safe Nether spawn found; generated an emergency obsidian landing pad at {}.", emergency);
        return emergency;
    }

    private static Path spawnAnchorPath(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("nether_only_spawn.txt");
    }

    private static BlockPos loadSpawnAnchor(ServerLevel level) {
        Path path = spawnAnchorPath(level);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            String[] coordinates = Files.readString(path).trim().split(",");
            if (coordinates.length != 3) {
                return null;
            }
            BlockPos loaded = new BlockPos(Integer.parseInt(coordinates[0]),
                    Integer.parseInt(coordinates[1]), Integer.parseInt(coordinates[2]));
            SPAWN_CACHE.put(level, loaded);
            return loaded;
        } catch (IOException | NumberFormatException exception) {
            LOGGER.warn("Could not read Nether spawn anchor; a new safe location will be selected.", exception);
            return null;
        }
    }

    private static void saveSpawnAnchor(ServerLevel level, BlockPos pos) {
        SPAWN_CACHE.put(level, pos);
        Path path = spawnAnchorPath(level);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, pos.getX() + "," + pos.getY() + "," + pos.getZ());
        } catch (IOException exception) {
            LOGGER.error("Could not save Nether spawn anchor. The spawn may be recalculated after restart.", exception);
        }
    }

    private static boolean isUsableSpawn(ServerLevel level, BlockPos feet) {
        // The anchor is stored separately from blocks, so even a completely mined pad
        // is rebuilt at the original coordinates rather than moving the spawn point.
        return feet != null && feet.getY() > level.getMinY() + 1
                && feet.getY() + 3 < level.getMinY() + level.getHeight();
    }

    private static boolean isSafeColumn(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinY() + 1
                || feet.getY() + 3 >= level.getMinY() + level.getHeight()) {
            return false;
        }
        BlockPos floorPos = feet.below();
        BlockState floor = level.getBlockState(floorPos);
        if (!floor.isFaceSturdy(level, floorPos, Direction.UP)
                || !floor.getFluidState().isEmpty() || isHazardous(floor)) {
            return false;
        }
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos headPos = feet.above(dy);
            BlockState state = level.getBlockState(headPos);
            if (!state.getCollisionShape(level, headPos).isEmpty()
                    || !state.getFluidState().isEmpty() || isHazardous(state)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafePatch(ServerLevel level, BlockPos center) {
        if (center.getY() <= level.getMinY() + 1
                || center.getY() + 3 >= level.getMinY() + level.getHeight()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos feet = center.offset(dx, 0, dz);
                BlockPos floorPos = feet.below();
                BlockState floor = level.getBlockState(floorPos);
                if (!floor.isFaceSturdy(level, floorPos, Direction.UP)
                        || !floor.getFluidState().isEmpty() || isHazardous(floor)) {
                    return false;
                }
                for (int dy = 0; dy <= 2; dy++) {
                    BlockState state = level.getBlockState(feet.above(dy));
                    if (!state.getCollisionShape(level, feet.above(dy)).isEmpty()
                            || !state.getFluidState().isEmpty() || isHazardous(state)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Rebuilds the complete 7x7 obsidian landing pad and clears player-sized headroom. */
    public static void rebuildSpawnPad(ServerLevel level, BlockPos feet) {
        // Ensure the saved anchor's chunk is loaded before changing blocks (including
        // when the player returns after a long absence or after the pad was mined).
        level.getChunkAt(feet);
        BlockPos floor = feet.below();
        for (int dx = -PAD_RADIUS; dx <= PAD_RADIUS; dx++) {
            for (int dz = -PAD_RADIUS; dz <= PAD_RADIUS; dz++) {
                BlockPos floorPos = floor.offset(dx, 0, dz);
                if (!level.getBlockState(floorPos).is(Blocks.OBSIDIAN)) {
                    level.setBlock(floorPos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos clearPos = feet.offset(dx, dy, dz);
                    BlockState current = level.getBlockState(clearPos);
                    if (!current.isAir()) {
                        level.setBlock(clearPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static boolean isHazardous(BlockState state) {
        return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW);
    }

    /** Direct server transfer: no portal creation, portal cooldown or portal animation. */
    public static void moveToNetherSpawn(ServerPlayer player, ServerLevel nether) {
        BlockPos safe = findSafeSpawn(nether);
        Vec3 position = new Vec3(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        player.teleport(new TeleportTransition(
                nether, position, Vec3.ZERO, player.getYRot(), player.getXRot(),
                TeleportTransition.DO_NOTHING
        ));
    }
}
