package me.dimensionforge.ore;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.ConfirmMenu;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.Text;
import org.bukkit.Material;
import org.bukkit.World;
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
        String world = viewer.getWorld().getName();
        boolean here = o.enabled && o.generate && o.appearsIn(world);
        Material icon = o.base().isItem() ? o.base() : Material.STONE;
        if (o.appearance == OreDef.Appearance.MODEL) {
            Integer cmd = plugin.packs().cmds().find("ore", o.modelId());
            if (cmd != null) {
                return ItemBuilder.of(plugin.packs().cmds().baseMaterial()).cmd(cmd).name((here ? "<green>" : "<gray>") + o.id)
                        .lore(loreOf(o, world, here)).glow(here).build();
            }
        }
        return ItemBuilder.of(icon).name((here ? "<green>" : "<gray>") + o.id).lore(loreOf(o, world, here)).glow(here).build();
    }

    private java.util.List<String> loreOf(OreDef o, String world, boolean here) {
        return java.util.List.of(
                here ? "<green>✔ Aparece en este mundo <dark_gray>(" + world + ")" : "<red>✘ No aparece en este mundo <dark_gray>(" + world + ")",
                "<gray>Mundos: <white>" + o.worldsText(),
                "<gray>Vetas por chunk: <white>" + o.veinsPerChunk + "  <gray>Altura: <white>" + o.yMin + " a " + o.yMax,
                "",
                "<yellow>Click izquierdo: <white>" + (here ? "quitar de este mundo" : "ACTIVAR en este mundo"),
                "<yellow>Click derecho: <white>editar todo",
                "<yellow>Shift + derecho: <white>borrar");
    }

    /** Activa o quita la mena en el mundo donde está el jugador; al activarla la genera alrededor al momento. */
    private void toggleHere(OreDef o) {
        World w = viewer.getWorld();
        String name = w.getName();
        if (o.appearsIn(name) && o.enabled && o.generate) {
            plugin.ores().toggleWorld(o, name);
            viewer.sendMessage(Text.mm("<yellow>" + o.id + " <gray>ya no aparecerá en <white>" + name + "<gray> (los ya generados se quedan)."));
        } else {
            o.enabled = true;
            o.generate = true;
            if (!o.appearsIn(name)) {
                plugin.ores().toggleWorld(o, name);
            } else {
                plugin.ores().changed();
            }
            int n = plugin.ores().regenAround(viewer.getLocation(), 3, o);
            viewer.sendMessage(Text.mm("<green>" + o.id + " <gray>aparecerá en <white>" + name
                    + "<gray>. Colocados <white>" + n + "<gray> bloques a tu alrededor; en chunks nuevos saldrá sola."));
        }
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
        } else if (c.right()) {
            new OreEditMenu(plugin, viewer, this, o).open();
        } else if (c.left()) {
            toggleHere(o);
            refresh();
        }
    }

    @Override
    protected void bottom() {
        set(46, ItemBuilder.of(Material.LIME_DYE).name("<green>Activar TODAS en este mundo")
                .lore("<gray>Click izquierdo: todas las menas aparecen aquí", "<gray>Click derecho: quitar todas de este mundo").build(), c -> {
            String name = viewer.getWorld().getName();
            for (OreDef o : plugin.ores().all()) {
                if (c.right()) {
                    if (o.appearsIn(name)) {
                        plugin.ores().toggleWorld(o, name);
                    }
                } else {
                    o.enabled = true;
                    o.generate = true;
                    if (!o.appearsIn(name)) {
                        plugin.ores().toggleWorld(o, name);
                    }
                }
            }
            plugin.ores().changed();
            if (!c.right()) {
                int n = plugin.ores().regenAround(viewer.getLocation(), 3, null);
                viewer.sendMessage(Text.mm("<green>Todas las menas activas en <white>" + name + "<gray>. Colocados <white>" + n + "<gray> bloques a tu alrededor."));
            } else {
                viewer.sendMessage(Text.mm("<yellow>Ninguna mena aparecerá ya en <white>" + name + "<gray>."));
            }
            refresh();
        });
        set(52, ItemBuilder.of(Material.TNT).name("<gold>Generar menas en este mundo")
                .lore("<gray>Para mundos que ya existen (mapas hechos,",
                        "<gray>WorldPainter...): alrededor de ti, franja del",
                        "<gray>world border o todo el mundo.").build(),
                c -> new OreGenerateMenu(plugin, viewer, this).open());
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
