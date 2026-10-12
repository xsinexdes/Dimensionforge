package me.dimensionforge.ore;

import org.bukkit.Material;
import org.bukkit.RegionAccessor;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Lógica de generación de vetas (usable desde el BlockPopulator y desde regen). */
public final class OreGenerator {

    private static final Set<Material> DEFAULT_REPLACE = EnumSet.of(Material.STONE, Material.DEEPSLATE, Material.ANDESITE,
            Material.DIORITE, Material.GRANITE, Material.TUFF, Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE,
            Material.END_STONE);

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** Cuadrado del world border: centro y medio lado. */
    public record Border(double cx, double cz, double half) {
        /** Distancia hacia dentro desde el borde (negativa = fuera del borde). */
        public double depth(double x, double z) {
            return half - Math.max(Math.abs(x - cx), Math.abs(z - cz));
        }
    }

    private OreGenerator() {
    }

    /** Borde del mundo, o null si no hay uno útil (mundo nulo o borde por defecto, enorme). */
    public static Border borderOf(World w) {
        if (w == null) {
            return null;
        }
        try {
            double size = w.getWorldBorder().getSize();
            if (size > 1.0E6) {
                return null;
            }
            return new Border(w.getWorldBorder().getCenter().getX(), w.getWorldBorder().getCenter().getZ(), size / 2.0);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Genera las vetas de las menas dadas dentro del chunk (cx, cz). Todas las posiciones se mantienen
     * dentro del propio chunk. Registra en {@code placed} las posiciones empaquetadas -> id de mena.
     */
    public static void generate(RegionAccessor acc, String worldName, int minH, int maxH, Random rnd, int cx, int cz,
                                List<OreDef> ores, Map<Integer, String> placed, Border border) {
        for (OreDef ore : ores) {
            if (!ore.enabled || !ore.generate) {
                continue;
            }
            Set<Material> replace = replaceSet(ore);
            int veins = (int) Math.floor(ore.veinsPerChunk);
            if (rnd.nextDouble() < ore.veinsPerChunk - veins) {
                veins++;
            }
            int lo = Math.max(ore.yMin, minH);
            int hi = Math.min(ore.yMax, maxH - 1);
            if (hi < lo) {
                continue;
            }
            if (ore.borderOnly) {
                if (border == null) {
                    continue; // sin world border no hay franja donde generar
                }
                double d = border.depth(cx * 16 + 8, cz * 16 + 8);
                if (d - 16 > ore.borderBand || d + 16 < 0) {
                    continue; // el chunk entero queda fuera de la franja
                }
            }
            int done = 0;
            int maxTries = ore.borderOnly ? veins * 4 + 4 : veins;
            for (int tries = 0; done < veins && tries < maxTries; tries++) {
                int x = cx * 16 + rnd.nextInt(16);
                int z = cz * 16 + rnd.nextInt(16);
                int y = lo + rnd.nextInt(hi - lo + 1);
                if (ore.borderOnly) {
                    double d = border.depth(x + 0.5, z + 0.5);
                    if (d < 0 || d > ore.borderBand) {
                        continue;
                    }
                }
                if (!ore.biomes.isEmpty() && !biomeAllowed(acc, ore, x, y, z)) {
                    continue;
                }
                vein(acc, rnd, ore, x, y, z, cx, cz, minH, maxH, replace, placed);
                done++;
            }
        }
    }

    private static Set<Material> replaceSet(OreDef ore) {
        if (ore.replace == null || ore.replace.isEmpty()) {
            return DEFAULT_REPLACE;
        }
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String s : ore.replace) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                set.add(m);
            }
        }
        return set.isEmpty() ? DEFAULT_REPLACE : set;
    }

    private static boolean biomeAllowed(RegionAccessor acc, OreDef ore, int x, int y, int z) {
        String current = normalize(String.valueOf(acc.getBiome(x, y, z)));
        for (String b : ore.biomes) {
            if (normalize(b).equals(current)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        String n = s.toLowerCase(Locale.ROOT).trim();
        int idx = n.indexOf(':');
        return idx >= 0 ? n.substring(idx + 1) : n;
    }

    private static void vein(RegionAccessor acc, Random rnd, OreDef ore, int x, int y, int z, int cx, int cz, int minH,
                             int maxH, Set<Material> replace, Map<Integer, String> placed) {
        List<int[]> frontier = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        frontier.add(new int[]{x, y, z});
        visited.add(key(x, y, z));
        int count = 0;
        int size = Math.max(1, ore.veinSize);
        int guard = size * 10 + 20;
        Material base = ore.base();
        while (count < size && !frontier.isEmpty() && guard-- > 0) {
            int idx = rnd.nextInt(frontier.size());
            int[] p = frontier.get(idx);
            int last = frontier.size() - 1;
            frontier.set(idx, frontier.get(last));
            frontier.remove(last);
            if ((p[0] >> 4) != cx || (p[2] >> 4) != cz || p[1] < minH || p[1] >= maxH) {
                continue;
            }
            Material cur = acc.getType(p[0], p[1], p[2]);
            if (replace.contains(cur)) {
                acc.setType(p[0], p[1], p[2], base);
                placed.put(OreManager.pack(p[0], p[1], p[2]), ore.id);
                count++;
            }
            for (int[] d : DIRS) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                int nz = p[2] + d[2];
                if (visited.add(key(nx, ny, nz))) {
                    frontier.add(new int[]{nx, ny, nz});
                }
            }
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) (y + 2048) & 0xFFFL);
    }
}
