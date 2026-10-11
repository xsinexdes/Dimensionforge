package me.dimensionforge.mob;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Reglas de aparición natural de un mob. */
public final class SpawnRulesMenu extends PagedMenu<SpawnRule> {

    private final MobDef def;

    public SpawnRulesMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, MobDef def) {
        super(plugin, viewer, parent);
        this.def = def;
    }

    @Override
    protected String titleKey() {
        return "gui.spawnrules-title";
    }

    @Override
    protected List<SpawnRule> items() {
        return new ArrayList<>(def.spawnRules);
    }

    @Override
    protected ItemStack render(SpawnRule r) {
        return ItemBuilder.of(Material.SPAWNER).name("<gold>" + r.mode + " · " + r.chance + "%")
                .lore("<gray>Mundo: <white>" + (r.dimension.isBlank() ? (def.dimension.isBlank() ? "cualquiera" : def.dimension) : r.dimension),
                        "<gray>Altura: <white>" + r.minY + " a " + r.maxY,
                        "<gray>Biomas: <white>" + (r.biomes.isEmpty() ? "todos" : String.join(", ", r.biomes)),
                        "<gray>Grupo: <white>" + r.groupMin + "-" + r.groupMax + " <gray>Máx/chunk: <white>" + r.maxPerChunk, "",
                        "<yellow>Click izq: <white>editar", "<yellow>Shift+der: <white>borrar").build();
    }

    @Override
    protected void onItemClick(SpawnRule r, MenuClick c) {
        if (c.right() && c.shift()) {
            def.spawnRules.remove(r);
            plugin.mobs().changed(def);
            refresh();
        } else if (c.left()) {
            new SpawnRuleEditMenu(plugin, viewer, this, def, r).open();
        }
    }

    @Override
    protected void bottom() {
        set(50, ItemBuilder.of(Material.LIME_CONCRETE).name("<green>Añadir regla").build(), c -> {
            SpawnRule r = new SpawnRule();
            r.dimension = def.dimension;
            def.spawnRules.add(r);
            plugin.mobs().changed(def);
            new SpawnRuleEditMenu(plugin, viewer, this, def, r).open();
        });
    }
}
