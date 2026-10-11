package me.dimensionforge.dimension;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/** Generador de terreno vacío (void). No genera ruido, superficie, cuevas, decoración ni estructuras. */
public final class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0.5, 64, 0.5);
    }
}
