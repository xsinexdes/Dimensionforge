package me.dimensionforge.dimension;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Confirma el entorno detectado antes de importar un mundo. */
public final class ImportConfirmMenu extends SettingsMenu {

    private final String folder;
    private World.Environment env;

    public ImportConfirmMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, String folder) {
        super(plugin, viewer, parent, "<gold>Importar: " + folder);
        this.folder = folder;
        this.env = plugin.dimensions().detectEnvironment(folder);
    }

    @Override
    protected void define() {
        info("Carpeta", Material.FILLED_MAP, folder);
        cycle("Entorno (detectado)", Material.GRASS_BLOCK, () -> env, v -> env = v,
                new World.Environment[]{World.Environment.NORMAL, World.Environment.NETHER, World.Environment.THE_END},
                "DIM-1 = nether, DIM1 = end, si no normal");
        action("CONFIRMAR IMPORTACIÓN", Material.LIME_CONCRETE, c -> {
            viewer.closeInventory();
            DimensionData d = plugin.dimensions().importWorld(folder, env, viewer);
            if (d != null) {
                new DimensionEditMenu(plugin, viewer, null, d).open();
            }
        });
    }
}
