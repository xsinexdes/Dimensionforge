package me.dimensionforge.mob;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/** Regla de aparición natural de un mob custom. */
public final class SpawnRule {

    /** REPLACE: sustituye apariciones naturales del tipo base. ADDITIVE: aparece además de cualquier mob hostil natural. */
    public enum Mode { REPLACE, ADDITIVE }

    /** Mundo/dimensión donde aplica ("" = cualquiera de la dimensión del mob). */
    public String dimension = "";
    public Mode mode = Mode.ADDITIVE;
    /** Probabilidad en porcentaje (0-100) por cada aparición natural candidata. */
    public double chance = 10.0;
    public int minY = -64;
    public int maxY = 320;
    public List<String> biomes = new ArrayList<>();
    public int groupMin = 1;
    public int groupMax = 1;
    /** Máximo de este mob en el chunk de aparición. */
    public int maxPerChunk = 3;

    public void save(ConfigurationSection s) {
        s.set("dimension", dimension);
        s.set("mode", mode.name());
        s.set("chance", chance);
        s.set("min-y", minY);
        s.set("max-y", maxY);
        s.set("biomes", biomes);
        s.set("group-min", groupMin);
        s.set("group-max", groupMax);
        s.set("max-per-chunk", maxPerChunk);
    }

    public static SpawnRule load(ConfigurationSection s) {
        SpawnRule r = new SpawnRule();
        r.dimension = s.getString("dimension", "");
        try {
            r.mode = Mode.valueOf(s.getString("mode", "ADDITIVE").toUpperCase());
        } catch (IllegalArgumentException e) {
            r.mode = Mode.ADDITIVE;
        }
        r.chance = s.getDouble("chance", 10.0);
        r.minY = s.getInt("min-y", -64);
        r.maxY = s.getInt("max-y", 320);
        r.biomes = new ArrayList<>(s.getStringList("biomes"));
        r.groupMin = Math.max(1, s.getInt("group-min", 1));
        r.groupMax = Math.max(r.groupMin, s.getInt("group-max", 1));
        r.maxPerChunk = Math.max(1, s.getInt("max-per-chunk", 3));
        return r;
    }
}
