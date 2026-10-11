package me.dimensionforge.portal;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Selector de dimensión/mundo destino. */
public final class DimensionPicker extends PagedMenu<String> {

    private final Consumer<String> onPick;

    public DimensionPicker(DimensionForgePlugin plugin, Player viewer, Menu parent, Consumer<String> onPick) {
        super(plugin, viewer, parent);
        this.onPick = onPick;
    }

    @Override
    protected String titleKey() {
        return "gui.picker-title";
    }

    @Override
    protected List<String> items() {
        Set<String> names = new LinkedHashSet<>(plugin.dimensions().names());
        for (World w : Bukkit.getWorlds()) {
            names.add(w.getName());
        }
        return new ArrayList<>(names);
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
        boolean managed = plugin.dimensions().get(name) != null;
        return ItemBuilder.of(managed ? Material.GRASS_BLOCK : Material.DIRT).name("<gold>" + name)
                .lore(managed ? "<green>Dimensión administrada" : "<gray>Mundo del servidor", "", "<yellow>Click: elegir").build();
    }

    @Override
    protected void onItemClick(String name, MenuClick c) {
        onPick.accept(name);
    }
}
