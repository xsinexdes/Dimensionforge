package me.dimensionforge.dimension;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Asistente de creación de dimensiones. */
public final class CreateDimensionMenu extends SettingsMenu {

    private String name = "";
    private World.Environment environment = World.Environment.NORMAL;
    private DimensionData.DimType type = DimensionData.DimType.NORMAL;
    private DimensionData.Generator generator = DimensionData.Generator.VANILLA;
    private String seed = "";
    private boolean structures = true;

    public CreateDimensionMenu(DimensionForgePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent, "<green>Crear dimensión");
    }

    @Override
    protected void define() {
        str("Nombre interno", Material.NAME_TAG, () -> name, v -> name = v.toLowerCase().trim(),
                "a-z, 0-9, _ y - (único)");
        cycle("Entorno", Material.GRASS_BLOCK, () -> environment, v -> environment = v,
                new World.Environment[]{World.Environment.NORMAL, World.Environment.NETHER, World.Environment.THE_END});
        cycle("Tipo de mundo", Material.MAP, () -> type, v -> type = v, DimensionData.DimType.values());
        cycle("Generador de terreno", Material.STONE, () -> generator, v -> generator = v,
                DimensionData.Generator.values(), "VOID = mundo vacío");
        str("Semilla", Material.WHEAT_SEEDS, () -> seed, v -> seed = v, "Texto o número; vacío = aleatoria");
        bool("Generar estructuras", Material.BRICKS, () -> structures, v -> structures = v);
        action("CREAR AHORA", Material.LIME_CONCRETE, c -> create(), "Crea el mundo y lo guarda en dimensions.yml");
    }

    private void create() {
        String err = plugin.dimensions().validateName(name);
        if (err != null) {
            plugin.messages().send(viewer, err, "name", name);
            return;
        }
        DimensionData d = new DimensionData(name);
        d.environment = environment;
        d.type = type;
        d.generator = generator;
        d.seedText = seed;
        d.structures = structures;
        viewer.closeInventory();
        plugin.dimensions().createAsync(d, viewer, () -> new DimensionEditMenu(plugin, viewer, null, d).open());
    }
}
