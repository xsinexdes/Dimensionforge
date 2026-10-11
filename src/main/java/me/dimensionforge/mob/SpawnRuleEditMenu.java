package me.dimensionforge.mob;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;

/** Edición de una regla de aparición. */
public final class SpawnRuleEditMenu extends SettingsMenu {

    private final MobDef def;
    private final SpawnRule r;

    public SpawnRuleEditMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, MobDef def, SpawnRule r) {
        super(plugin, viewer, parent, "<dark_gray>Regla de aparición");
        this.def = def;
        this.r = r;
    }

    @Override
    protected void changed() {
        plugin.mobs().changed(def);
    }

    @Override
    protected void define() {
        cycle("Modo", Material.COMPARATOR, () -> r.mode, v -> r.mode = v, SpawnRule.Mode.values(),
                "REPLACE: sustituye apariciones del tipo base", "ADDITIVE: aparece junto a cualquier mob hostil natural");
        dbl("Probabilidad (%)", Material.GOLD_NUGGET, () -> r.chance, v -> r.chance = v, 1, 0, 100,
                "Por cada aparición natural candidata");
        str("Mundo", Material.GRASS_BLOCK, () -> r.dimension, v -> r.dimension = v.trim(), "Vacío = la dimensión del mob");
        intVal("Altura mínima", Material.LADDER, () -> r.minY, v -> r.minY = v, 8, -64, 320);
        intVal("Altura máxima", Material.LADDER, () -> r.maxY, v -> r.maxY = v, 8, -64, 320);
        str("Biomas (separados por coma)", Material.OAK_SAPLING, () -> String.join(",", r.biomes), v -> {
            r.biomes = new ArrayList<>();
            for (String b : Arrays.asList(v.split(","))) {
                if (!b.isBlank()) {
                    r.biomes.add(b.trim().toLowerCase());
                }
            }
        }, "Claves sin namespace, ej. plains,forest. Vacío = todos");
        intVal("Grupo mínimo", Material.ZOMBIE_HEAD, () -> r.groupMin, v -> {
            r.groupMin = v;
            if (r.groupMax < v) {
                r.groupMax = v;
            }
        }, 1, 1, 16);
        intVal("Grupo máximo", Material.ZOMBIE_HEAD, () -> r.groupMax, v -> r.groupMax = Math.max(v, r.groupMin), 1, 1, 16);
        intVal("Máximo por chunk", Material.CHEST, () -> r.maxPerChunk, v -> r.maxPerChunk = v, 1, 1, 64);
    }
}
