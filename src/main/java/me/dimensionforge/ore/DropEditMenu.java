package me.dimensionforge.ore;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Edita un drop concreto. */
public final class DropEditMenu extends SettingsMenu {

    private final DropEntry d;
    private final Runnable onChange;

    public DropEditMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, DropEntry d, Runnable onChange) {
        super(plugin, viewer, parent, "<gold>Editar drop");
        this.d = d;
        this.onChange = onChange;
    }

    @Override
    protected void changed() {
        onChange.run();
    }

    @Override
    protected void define() {
        str("Material", d.mat(), () -> d.material, v -> {
            Material m = Material.matchMaterial(v.trim());
            if (m != null && m.isItem() && !m.isAir()) {
                d.material = m.name();
            } else {
                plugin.messages().send(viewer, "error.invalid-material", "value", v);
            }
        });
        intVal("Cantidad mínima", Material.IRON_NUGGET, () -> d.min, v -> {
            d.min = v;
            if (d.max < d.min) {
                d.max = d.min;
            }
        }, 1, 0, 2304);
        intVal("Cantidad máxima", Material.GOLD_NUGGET, () -> d.max, v -> {
            d.max = v;
            if (d.min > d.max) {
                d.min = d.max;
            }
        }, 1, 0, 2304);
        dbl("Probabilidad (%)", Material.EMERALD, () -> d.chance, v -> d.chance = v, 5, 0, 100);
        intVal("CustomModelData (0 = ninguno)", Material.PAINTING, () -> d.cmd, v -> d.cmd = v, 1, 0, Integer.MAX_VALUE);
        str("Nombre (MiniMessage)", Material.NAME_TAG, () -> d.name, v -> d.name = v);
        action("Quitar lore", Material.BARRIER, c -> {
            d.lore.clear();
            changed();
            refresh();
        }, "Líneas de lore: " + d.lore.size());
        action("Añadir línea de lore", Material.WRITABLE_BOOK, c -> plugin.textInput().request(viewer, "Nueva línea de lore", "", text -> {
            d.lore.add(text);
            changed();
            open();
        }, this::open));
    }
}
