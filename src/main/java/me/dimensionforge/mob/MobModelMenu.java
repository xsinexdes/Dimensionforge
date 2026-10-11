package me.dimensionforge.mob;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Selector de modelos de BetterModel con búsqueda, recarga y prueba. */
public final class MobModelMenu extends PagedMenu<String> {

    private final MobDef def;

    public MobModelMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, MobDef def) {
        super(plugin, viewer, parent);
        this.def = def;
    }

    @Override
    protected String titleKey() {
        return "gui.models-title";
    }

    @Override
    protected List<String> items() {
        return plugin.models().modelNames();
    }

    @Override
    protected boolean searchable() {
        return true;
    }

    @Override
    protected boolean matches(String item, String query) {
        return item.toLowerCase().contains(query);
    }

    @Override
    protected ItemStack render(String name) {
        boolean sel = name.equals(def.model);
        int anims = plugin.models().animations(name).size();
        return ItemBuilder.of(sel ? Material.ARMOR_STAND : Material.PAPER).name((sel ? "<green>" : "<yellow>") + Text.escape(name))
                .lore("<gray>Animaciones: <white>" + anims, "",
                        "<yellow>Click izq: <white>usar este modelo",
                        "<yellow>Click der: <white>probar (aparece delante de ti)").glow(sel).build();
    }

    @Override
    protected void onItemClick(String name, MenuClick c) {
        if (c.right()) {
            if (plugin.mobs().testModel(viewer, name, def.modelScale)) {
                plugin.messages().send(viewer, "mob.model-test", "model", name);
                viewer.closeInventory();
            } else {
                plugin.messages().send(viewer, "mob.model-test-failed", "model", name);
            }
            return;
        }
        def.model = name;
        plugin.mobs().changed(def);
        plugin.messages().send(viewer, "mob.model-set", "model", name, "id", def.id);
        refresh();
    }

    @Override
    protected void bottom() {
        set(50, ItemBuilder.of(Material.SUNFLOWER).name("<aqua>Recargar lista")
                .lore("<gray>Vuelve a leer los modelos cargados", "<gray>en " + plugin.models().provider(),
                        "<gray>Modelos ahora: <white>" + plugin.models().modelNames().size()).build(), c -> {
            plugin.messages().send(viewer, "mob.models-reloaded", "count", plugin.models().modelNames().size());
            refresh();
        });
        set(51, ItemBuilder.of(Material.BARRIER).name("<red>Quitar modelo (modo simple)").build(), c -> {
            def.model = "";
            plugin.mobs().changed(def);
            refresh();
        });
        if (!plugin.models().available()) {
            set(46, ItemBuilder.of(Material.REDSTONE_TORCH).name("<red>BetterModel no está instalado")
                    .lore("<gray>Instala BetterModel para ver modelos.", "<gray>Mientras tanto el mob usa su", "<gray>apariencia vanilla (modo simple).").build());
        }
    }
}
