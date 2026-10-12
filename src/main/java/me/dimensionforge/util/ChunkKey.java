package me.dimensionforge.util;

import org.bukkit.Chunk;
import org.bukkit.Location;

/** Clave inmutable de chunk (mundo + coordenadas). */
public record ChunkKey(String world, int x, int z) {

    public static ChunkKey of(Chunk c) {
        return new ChunkKey(c.getWorld().getName(), c.getX(), c.getZ());
    }

    public static ChunkKey of(Location l) {
        return new ChunkKey(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4);
    }

    public static ChunkKey ofBlock(String world, int bx, int bz) {
        return new ChunkKey(world, bx >> 4, bz >> 4);
    }
}
