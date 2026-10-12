package me.dimensionforge.ore;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Panel para generar las menas en mundos que ya existen (los mundos nuevos las generan solos). */
public final class OreGenerateMenu extends Menu {

    public OreGenerateMenu(DimensionForgePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_purple>Generar menas en este mundo");
    }

    @Override
    protected int rows() {
        return 3;
    }

    private void run(OreManager.FillMode mode, int radius) {
        viewer.closeInventory();
        plugin.ores().startFill(viewer, viewer.getWorld(), mode, viewer.getLocation(), radius);
    }

    @Override
    protected void build() {
        String world = viewer.getWorld().getName();
        set(4, ItemBuilder.of(Material.PAPER).name("<yellow>Mundo: <white>" + world)
                .lore("<gray>Mundos NUEVOS: las menas salen solas.",
                        "<gray>Mundos que ya existen: usa estos botones.",
                        "<gray>Cada mena se aplica una sola vez por chunk,",
                        "<gray>así que puedes repetirlos sin duplicar.",
                        "<gray>Antes activa las menas con click izquierdo",
                        "<gray>en la lista (o «Activar TODAS»).").build());
        set(10, ItemBuilder.of(Material.COMPASS).name("<green>Alrededor de mí: 10 chunks")
                .lore("<gray>Unos 320x320 bloques. Rápido.").build(), c -> run(OreManager.FillMode.AROUND, 10));
        set(11, ItemBuilder.of(Material.FILLED_MAP).name("<green>Alrededor de mí: 30 chunks")
                .lore("<gray>Unos 960x960 bloques. Tarda un poco.").build(), c -> run(OreManager.FillMode.AROUND, 30));
        set(13, ItemBuilder.of(Material.BARRIER).name("<aqua>Franja del world border")
                .lore("<gray>Solo la franja de las menas con",
                        "<gray>«Solo cerca del world border».",
                        "<gray>Necesita /worldborder configurado.").build(), c -> run(OreManager.FillMode.BORDER, 0));
        set(14, ItemBuilder.of(Material.GRASS_BLOCK).name("<aqua>Todo el mundo (dentro del borde)")
                .lore("<red>Puede tardar mucho en mapas grandes.",
                        "<gray>Necesita /worldborder configurado.").build(), c -> run(OreManager.FillMode.ALL, 0));
        set(16, ItemBuilder.of(Material.RED_DYE).name("<red>Parar la generación en curso")
                .lore(plugin.ores().fillRunning() ? "<green>Hay una en marcha" : "<gray>No hay ninguna en marcha").build(), c -> {
            plugin.ores().stopFill();
            viewer.sendMessage(Text.mm("<yellow>Generación detenida."));
            refresh();
        });
        set(22, ItemBuilder.of(Material.ARROW).name(plugin.messages().raw("gui.back")).build(), c -> {
            if (parent != null) {
                parent.open();
            } else {
                viewer.closeInventory();
            }
        });
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }
}
