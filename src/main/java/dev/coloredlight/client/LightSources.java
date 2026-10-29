package dev.coloredlight.client;

import dev.coloredlight.core.LightColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

public final class LightSources {
    private static final Map<Block, String> NAMES = new IdentityHashMap<>();
    private static final Map<Block, Integer> COLORS = new IdentityHashMap<>();
    private static final Map<Block, Integer> FILTERS = new IdentityHashMap<>();

    private LightSources() {
    }

    public static boolean maybeLight(BlockState state) {
        return state.getLightEmission() > 0;
    }

    public static boolean maybeGlass(BlockState state) {
        return filter(state) != LightColor.NONE;
    }

    public static int color(BlockState state) {
        return COLORS.computeIfAbsent(state.getBlock(), block -> LightColor.forBlock(name(block)));
    }

    public static int filter(BlockState state) {
        return FILTERS.computeIfAbsent(state.getBlock(), block -> LightColor.filter(name(block)));
    }

    public static String name(Block block) {
        return NAMES.computeIfAbsent(block, b -> {
            String id = b.getDescriptionId();
            return id.substring(id.lastIndexOf('.') + 1);
        });
    }
}
