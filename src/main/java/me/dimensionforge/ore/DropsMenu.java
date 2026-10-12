package me.dimensionforge.ore;

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

/** Lista editable de drops (se usa para menas y mobs). */
public final class DropsMenu extends PagedMenu<DropEntry> {

    private final String title;
    private final List<DropEntry> list;
    private final Runnable onChange;

    public DropsMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, String title, List<DropEntry> list, Runnable onChange) {
        super(plugin, viewer, parent);
        this.title = title;
        this.list = list;
        this.onChange = onChange;
    }

    @Override
    protected String titleKey() {
        return "gui.drops-title";
    }

    @Override
    protected List<DropEntry> items() {
        return new ArrayList<>(list);
    }

    @Override
    protected ItemStack render(DropEntry d) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Cantidad: <white>" + d.min + "-" + d.max);
        lore.add("<gray>Probabilidad: <white>" + d.chance + "%");
        lore.add("<gray>CustomModelData: <white>" + (d.cmd > 0 ? d.cmd : "ninguno"));
        if (!d.name.isBlank()) {
            lore.add("<gray>Nombre: <white>" + me.dimensionforge.util.Text.escape(d.name));
        }
        lore.add("");
        lore.add("<yellow>Click izq: <white>editar");
        lore.add("<yellow>Click der: <white>quitar");
        return ItemBuilder.of(d.mat()).name("<gold>" + d.material).lore(lore).cmd(d.cmd > 0 ? d.cmd : null).build();
    }

    @Override
    protected void onItemClick(DropEntry d, MenuClick c) {
        if (c.right()) {
            list.remove(d);
            onChange.run();
            refresh();
        } else if (c.left()) {
            new DropEditMenu(plugin, viewer, this, d, onChange).open();
        }
    }

    @Override
    protected void bottom() {
        set(50, ItemBuilder.of(Material.HOPPER).name("<green>Añadir el ítem de mi mano")
                .lore("<gray>Copia material, nombre, lore,", "<gray>CustomModelData y cantidad").build(), c -> {
            ItemStack hand = viewer.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                plugin.messages().send(viewer, "error.empty-hand");
                return;
            }
            list.add(DropEntry.fromItem(hand));
            onChange.run();
            refresh();
        });
        set(51, ItemBuilder.of(Material.DIAMOND).name("<yellow>Añadir drop vacío (diamante)").build(), c -> {
            list.add(new DropEntry());
            onChange.run();
            refresh();
        });
    }

    @Override
    protected net.kyori.adventure.text.Component title() {
        return me.dimensionforge.util.Text.mm(title);
    }
}
