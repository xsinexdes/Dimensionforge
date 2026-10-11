package me.dimensionforge.dimension;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.ConfirmMenu;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.ore.OreListMenu;
import me.dimensionforge.pack.PackMenu;
import me.dimensionforge.portal.PortalListMenu;
import me.dimensionforge.skill.SkillLibraryMenu;
import me.dimensionforge.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Menú principal: lista paginada de dimensiones + accesos a todas las secciones. */
public final class MainMenu extends PagedMenu<DimensionData> {

    public MainMenu(DimensionForgePlugin plugin, Player viewer) {
        super(plugin, viewer, null);
    }

    @Override
    protected String titleKey() {
        return "gui.main-title";
    }

    @Override
    protected List<DimensionData> items() {
        return new ArrayList<>(plugin.dimensions().all());
    }

    @Override
    protected ItemStack render(DimensionData d) {
        World w = plugin.dimensions().world(d.name);
        Material icon = switch (d.environment) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
        int players = w == null ? 0 : w.getPlayers().size();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Entorno: <white>" + d.environment.name());
        lore.add("<gray>Tipo: <white>" + d.type.name() + (d.voidTerrain() ? " <dark_gray>(vacío)" : ""));
        lore.add("<gray>Jugadores dentro: <white>" + players);
        lore.add("<gray>Estado: " + (w != null ? "<green>Cargada" : "<red>Descargada"));
        lore.add("");
        lore.add("<yellow>Click izq: <white>teletransportar");
        lore.add("<yellow>Click der: <white>editar");
        lore.add("<yellow>Shift+der: <white>borrar");
        return ItemBuilder.of(icon).name("<gold>" + d.name).lore(lore).glow(w != null).build();
    }

    @Override
    protected void onItemClick(DimensionData d, MenuClick c) {
        if (c.right() && c.shift()) {
            confirmDelete(d);
        } else if (c.right()) {
            new DimensionEditMenu(plugin, viewer, this, d).open();
        } else if (c.left()) {
            viewer.closeInventory();
            plugin.dimensions().teleport(viewer, d);
        }
    }

    private void confirmDelete(DimensionData d) {
        confirmDelete(plugin, viewer, d, this);
    }

    /** Flujo de borrado con doble confirmación para la carpeta del mundo. */
    static void confirmDelete(DimensionForgePlugin plugin, Player viewer, DimensionData d, Menu back) {
        new ConfirmMenu(plugin, viewer, "<red>¿Quitar la dimensión " + d.name + "?",
                List.of("<gray>Se evacuará a los jugadores y se", "<gray>descargará el mundo."), () -> {
            new ConfirmMenu(plugin, viewer, "<red>¿Borrar TAMBIÉN la carpeta?",
                    List.of("<gray>Confirmar = borrar la carpeta del mundo", "<gray>Cancelar = solo quitar la dimensión"), () -> {
                new ConfirmMenu(plugin, viewer, "<dark_red>ÚLTIMO AVISO: carpeta irrecuperable",
                        List.of("<red>Se borrará " + d.name + " del disco."), () -> doDelete(plugin, viewer, d, true),
                        back::open).open();
            }, () -> doDelete(plugin, viewer, d, false)).open();
        }, back::open).open();
    }

    private static void doDelete(DimensionForgePlugin plugin, Player viewer, DimensionData d, boolean folder) {
        boolean ok = plugin.dimensions().delete(d, folder);
        if (ok) {
            plugin.messages().send(viewer, folder ? "dimension.deleted-folder" : "dimension.deleted", "name", d.name);
        } else {
            plugin.messages().send(viewer, "dimension.delete-failed", "name", d.name);
        }
        new MainMenu(plugin, viewer).open();
    }

    @Override
    protected void bottom() {
        set(46, ItemBuilder.of(Material.GRASS_BLOCK).name("<green>Crear dimensión").lore("<gray>Asistente por menú").build(),
                c -> new CreateDimensionMenu(plugin, viewer, this).open());
        set(47, ItemBuilder.of(Material.BOOK).name("<aqua>Resource pack").lore("<gray>Generar y enviar el pack").build(),
                c -> new PackMenu(plugin, viewer, this).open());
        set(50, ItemBuilder.of(Material.CHEST_MINECART).name("<yellow>Importar mundo existente")
                .lore("<gray>Carpetas con level.dat del servidor").build(), c -> new ImportWorldMenu(plugin, viewer, this).open());
        set(51, ItemBuilder.of(Material.GOLDEN_AXE).name("<light_purple>Portales").lore("<gray>Listar y editar portales").build(),
                c -> new PortalListMenu(plugin, viewer, this).open());
        set(52, ItemBuilder.of(Material.ENCHANTED_BOOK).name("<red>Biblioteca de habilidades")
                .lore("<gray>Explorar las " + plugin.skills().count() + " habilidades").build(),
                c -> new SkillLibraryMenu(plugin, viewer, this, null).open());
    }
}
