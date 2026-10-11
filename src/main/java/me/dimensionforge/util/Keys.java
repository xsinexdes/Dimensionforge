package me.dimensionforge.util;

import me.dimensionforge.DimensionForgePlugin;
import org.bukkit.NamespacedKey;

/** Claves de PersistentDataContainer del plugin. */
public final class Keys {

    public static NamespacedKey WAND;
    public static NamespacedKey CUSTOM_MOB;
    public static NamespacedKey CHUNK_ORES;
    public static NamespacedKey ORE_DISPLAY;
    public static NamespacedKey SKILL_SUMMON;

    private Keys() {
    }

    public static void init(DimensionForgePlugin plugin) {
        WAND = new NamespacedKey(plugin, "portal_wand");
        CUSTOM_MOB = new NamespacedKey(plugin, "custom_mob");
        CHUNK_ORES = new NamespacedKey(plugin, "chunk_ores");
        ORE_DISPLAY = new NamespacedKey(plugin, "ore_display");
        SKILL_SUMMON = new NamespacedKey(plugin, "skill_summon");
    }
}
