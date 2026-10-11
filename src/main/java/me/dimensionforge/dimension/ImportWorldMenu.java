package me.dimensionforge.dimension;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Lista las carpetas con level.dat que se pueden importar. */
public final class ImportWorldMenu extends PagedMenu<String> {

    public ImportWorldMenu(DimensionForgePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected String titleKey() {
        return "gui.import-title";
    }

    @Override
    protected List<String> items() {
        return plugin.dimensions().scanImportable();
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
    protected ItemStack render(String folder) {
        World.Environment env = plugin.dimensions().detectEnvironment(folder);
        return ItemBuilder.of(Material.FILLED_MAP).name("<gold>" + folder)
                .lore("<gray>Entorno detectado: <white>" + env.name(), "", "<yellow>Click: importar").build();
    }

    @Override
    protected void onItemClick(String folder, MenuClick c) {
        new ImportConfirmMenu(plugin, viewer, this, folder).open();
    }
}
