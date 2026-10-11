package me.dimensionforge.ore;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Elige en qué mundos aparece una mena: un clic activa o desactiva cada mundo (puede ser más de uno). */
public final class WorldPickMenu extends PagedMenu<String> {

    private final OreDef ore;

    public WorldPickMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, OreDef ore) {
        super(plugin, viewer, parent);
        this.ore = ore;
    }

    @Override
    protected String titleKey() {
        return "gui.settings-title";
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_gray>Mundos de " + Text.escape(ore.id));
    }

    /** Mundos cargados, más los ya elegidos aunque ahora estén descargados. */
    @Override
    protected List<String> items() {
        TreeSet<String> names = new TreeSet<>();
        for (World w : Bukkit.getWorlds()) {
            names.add(w.getName());
        }
        names.addAll(ore.worlds);
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
        boolean on = ore.appearsIn(name);
        World w = Bukkit.getWorld(name);
        Material icon = w == null ? Material.BARRIER : switch (w.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
        return ItemBuilder.of(icon).name((on ? "<green>" : "<gray>") + Text.escape(name))
                .lore(on ? "<green>La mena aparece aquí" : "<gray>La mena no aparece aquí",
                        w == null ? "<red>Mundo descargado" : "<dark_gray>Mundo cargado",
                        "", "<yellow>Click: <white>" + (on ? "quitar" : "añadir"))
                .glow(on).build();
    }

    @Override
    protected void onItemClick(String name, MenuClick click) {
        plugin.ores().toggleWorld(ore, name);
        refresh();
    }

    @Override
    protected void bottom() {
        set(46, ItemBuilder.of(Material.LIME_DYE).name("<green>Todos los mundos cargados")
                .lore("<gray>Activa la mena en cada mundo del servidor").build(), c -> {
            for (World w : Bukkit.getWorlds()) {
                if (!ore.appearsIn(w.getName())) {
                    plugin.ores().toggleWorld(ore, w.getName());
                }
            }
            refresh();
        });
        set(50, ItemBuilder.of(Material.GRAY_DYE).name("<red>Quitar todos")
                .lore("<gray>La mena no se generará en ningún mundo").build(), c -> {
            ore.worlds.clear();
            plugin.ores().changed();
            refresh();
        });
        set(51, ItemBuilder.of(Material.PAPER).name("<yellow>Importante")
                .lore("<gray>Solo se generan en chunks NUEVOS.",
                        "<gray>Para chunks ya creados usa en el mundo:",
                        "<white>/dimension ore regen").build());
    }
}
