package dev.coloredlight.client;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class Levels {
    private Levels() {
    }

    public static int minSectionY(LevelHeightAccessor level) {
        return level.getMinSectionY();
    }

    public static int maxSectionY(LevelHeightAccessor level) {
        return level.getMaxSectionY();
    }

    public static ChunkAccess chunk(ClientLevel level, int x, int z) {
        return level.getChunk(x, z, ChunkStatus.FULL, false);
    }

    public static int chunkX(ChunkPos pos) {
        return pos.x();
    }

    public static int chunkZ(ChunkPos pos) {
        return pos.z();
    }

    public static boolean mayHaveLights(LevelChunkSection section) {
        return section != null && !section.hasOnlyAir() && section.maybeHas(LightSources::maybeLight);
    }

    public static boolean mayHaveGlass(LevelChunkSection section) {
        return section != null && !section.hasOnlyAir() && section.maybeHas(LightSources::maybeGlass);
    }

    public static int lightBlock(BlockState state) {
        return state.getLightDampening();
    }

    public static boolean onlyAir(LevelChunkSection section) {
        return section.hasOnlyAir();
    }
}
