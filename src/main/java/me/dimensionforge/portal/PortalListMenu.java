package me.dimensionforge.portal;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.ConfirmMenu;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Lista de portales: teletransportar, editar o borrar. */
public final class PortalListMenu extends PagedMenu<Portal> {

    public PortalListMenu(DimensionForgePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected String titleKey() {
        return "gui.portals-title";
    }

    @Override
    protected List<Portal> items() {
        return new ArrayList<>(plugin.portals().all());
    }

    @Override
    protected boolean searchable() {
        return true;
    }

    @Override
    protected boolean matches(Portal item, String query) {
        return item.id.contains(query) || item.destination.toLowerCase().contains(query);
    }

    @Override
    protected ItemStack render(Portal p) {
        return ItemBuilder.of(Material.OBSIDIAN).name("<light_purple>" + p.id).lore(
                "<gray>Mundo: <white>" + p.world,
                "<gray>Región: <white>" + p.x1 + "," + p.y1 + "," + p.z1 + " → " + p.x2 + "," + p.y2 + "," + p.z2,
                "<gray>Destino: <white>" + p.destination,
                "<gray>Activación: <white>" + p.trigger.name(),
                "", "<yellow>Click izq: <white>ir al portal", "<yellow>Click der: <white>editar",
                "<yellow>Shift+der: <white>borrar").build();
    }

    @Override
    protected void onItemClick(Portal p, MenuClick c) {
        if (c.right() && c.shift()) {
            new ConfirmMenu(plugin, viewer, "<red>¿Borrar el portal " + p.id + "?", List.of("<gray>También se limpia su relleno."), () -> {
                plugin.portals().remove(p);
                plugin.messages().send(viewer, "portal.deleted", "id", p.id);
                new PortalListMenu(plugin, viewer, parent).open();
            }, this::open).open();
        } else if (c.right()) {
            new PortalEditMenu(plugin, viewer, this, p, false).open();
        } else if (c.left()) {
            World w = Bukkit.getWorld(p.world);
            if (w == null) {
                plugin.messages().send(viewer, "portal.world-not-loaded", "world", p.world);
                return;
            }
            viewer.closeInventory();
            viewer.teleportAsync(new Location(w, p.centerX(), p.y1, p.z1 - 1.5));
        }
    }

    @Override
    protected void bottom() {
        set(50, ItemBuilder.of(Material.LIME_CONCRETE).name("<green>Crear portal")
                .lore("<gray>Usa tu selección del hacha", "<gray>(/dimension wand)").build(), c -> startCreate(plugin, viewer, this));
        set(51, ItemBuilder.of(Material.GOLDEN_AXE).name("<yellow>Obtener Hacha de Portales").build(), c -> {
            viewer.getInventory().addItem(plugin.wand().create());
            plugin.messages().send(viewer, "wand.given");
        });
    }

    /** Valida la selección y abre el menú de creación. */
    public static void startCreate(DimensionForgePlugin plugin, Player viewer, Menu parent) {
        PortalWand.Selection s = plugin.wand().get(viewer);
        if (s == null || !s.complete()) {
            plugin.messages().send(viewer, "portal.need-selection");
            viewer.closeInventory();
            return;
        }
        Portal draft = new Portal("");
        draft.setRegion(s.world, s.p1[0], s.p1[1], s.p1[2], s.p2[0], s.p2[1], s.p2[2]);
        draft.cooldown = plugin.getConfig().getInt("portals.default-cooldown-seconds", 3);
        draft.sound = plugin.getConfig().getString("portals.default-sound", "entity.enderman.teleport");
        new PortalEditMenu(plugin, viewer, parent, draft, true).open();
    }
}
