package me.dimensionforge.ore;

import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** BlockPopulator que coloca las vetas de menas custom en chunks nuevos. */
public final class OrePopulator extends BlockPopulator {

    private final OreManager ores;

    public OrePopulator(OreManager ores) {
        this.ores = ores;
    }

    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull LimitedRegion region) {
        List<OreDef> list = ores.generatorsFor(worldInfo.getName());
        if (list.isEmpty()) {
            return;
        }
        Map<Integer, String> placed = new HashMap<>();
        try {
            OreGenerator.generate(region, worldInfo.getName(), worldInfo.getMinHeight(), worldInfo.getMaxHeight(), random,
                    chunkX, chunkZ, list, placed);
        } catch (RuntimeException ex) {
            // un fallo de generación nunca debe romper la generación del chunk
            return;
        }
        ores.registerGenerated(worldInfo.getName(), chunkX, chunkZ, placed);
    }
}
