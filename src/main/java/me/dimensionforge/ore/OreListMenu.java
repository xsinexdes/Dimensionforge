package me.dimensionforge.ore;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.ConfirmMenu;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Menas custom de una dimensión; con dimensión vacía lista todas (salen solas las que tienen PNG en el pack). */
public final class OreListMenu extends PagedMenu<OreDef> {

    private final String dimension;

    public OreListMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, String dimension) {
        super(plugin, viewer, parent);
        this.dimension = dimension;
    }

    @Override
    protected String titleKey() {
        return "gui.ores-title";
    }

    @Override
    protected List<OreDef> items() {
        return plugin.ores().forDimension(dimension);
    }

    @Override
    protected boolean searchable() {
        return true;
    }

    @Override
    protected boolean matches(OreDef item, String query) {
        return item.id.contains(query);
    }

    @Override
    protected ItemStack render(OreDef o) {
        return ItemBuilder.of(o.base().isItem() ? o.base() : Material.STONE).name((o.enabled ? "<green>" : "<red>") + o.id).lore(
                "<gray>Mundos: <white>" + o.worldsText(),
                "<gray>Bloque base: <white>" + o.baseMaterial,
                "<gray>Dureza: <white>" + o.hardness + " s",
                "<gray>Herramienta: <white>" + (o.toolMode == OreDef.ToolMode.TIER ? o.minTier + (o.pickaxeOnly ? " (pico)" : "") : o.toolMaterial),
                "<gray>Vetas/chunk: <white>" + o.veinsPerChunk + " <gray>tamaño <white>" + o.veinSize,
                "<gray>Estado: " + (o.enabled ? "<green>activa" : "<red>desactivada"), "",
                "<yellow>Click izq: <white>editar", "<yellow>Shift+der: <white>borrar").glow(o.enabled).build();
    }

    @Override
    protected void onItemClick(OreDef o, MenuClick c) {
        if (c.right() && c.shift()) {
            new ConfirmMenu(plugin, viewer, "<red>¿Borrar la mena " + o.id + "?",
                    List.of("<gray>Los bloques ya generados quedan", "<gray>como bloques vanilla."), () -> {
                plugin.ores().remove(o);
                plugin.messages().send(viewer, "ore.removed", "id", o.id);
                new OreListMenu(plugin, viewer, parent, dimension).open();
            }, this::open).open();
        } else if (c.left()) {
            new OreEditMenu(plugin, viewer, this, o).open();
        }
    }

    @Override
    protected void bottom() {
        set(51, ItemBuilder.of(Material.SPYGLASS).name("<aqua>Buscar texturas nuevas")
                .lore("<gray>Crea una mena por cada PNG nuevo de", "<gray>pack/textures/ore/ y reconstruye el pack").build(), c -> {
            plugin.messages().send(viewer, "pack.building");
            plugin.packs().build(viewer, () -> {
                plugin.ores().syncTextures();
                refresh();
            });
        });
        set(50, ItemBuilder.of(Material.LIME_CONCRETE).name("<green>Crear mena").lore("<gray>Pide un id y el bloque base").build(),
                c -> plugin.textInput().request(viewer, "Id de la mena (a-z, 0-9, _ -)", "", id -> {
                    String clean = id.toLowerCase().trim();
                    if (!clean.matches("[a-z0-9_-]{1,32}") || plugin.ores().get(clean) != null) {
                        plugin.messages().send(viewer, "error.ore-id-invalid", "id", id);
                        open();
                        return;
                    }
                    Material hand = viewer.getInventory().getItemInMainHand().getType();
                    String initial = hand.isBlock() && !hand.isAir() ? hand.name() : "DEEPSLATE_IRON_ORE";
                    plugin.textInput().request(viewer, "Bloque base (material vanilla)", initial, mat -> {
                        Material m = Material.matchMaterial(mat.trim());
                        if (m == null || !m.isBlock() || m.isAir()) {
                            plugin.messages().send(viewer, "error.invalid-material", "value", mat);
                            open();
                            return;
                        }
                        OreDef o = plugin.ores().create(dimension, clean, m);
                        plugin.messages().send(viewer, "ore.created", "id", o.id);
                        new OreEditMenu(plugin, viewer, this, o).open();
                    }, this::open);
                }, this::open));
    }
}
