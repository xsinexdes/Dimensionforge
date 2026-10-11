package me.dimensionforge.pack;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import me.dimensionforge.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Gestión del resource pack: reconstruir, enviar, ver validaciones. */
public final class PackMenu extends SettingsMenu {

    public PackMenu(DimensionForgePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent, "<dark_gray>Resource pack");
    }

    @Override
    protected void define() {
        PackManager pm = plugin.packs();
        info("Estado", Material.PAPER, "Modelos convertidos: " + pm.modelCount(),
                "CMD registrados: " + pm.cmds().size(), "SHA-1: " + (pm.hash().isEmpty() ? "(sin pack)" : pm.shortHash() + "…"),
                "Avisos: " + pm.warnings().size() + " · Errores: " + pm.errors().size(),
                "URL: " + (pm.url() == null ? "no configurada (pack.host.*)" : "lista"));
        action("Reconstruir pack", Material.ANVIL, c -> {
            plugin.messages().send(viewer, "pack.building");
            pm.build(viewer, this::refresh);
        }, "Lee pack/models/<tipo>/*.bbmodel y genera el zip");
        action("Enviármelo", Material.ENDER_PEARL, c -> {
            if (!pm.send(viewer)) {
                plugin.messages().send(viewer, "pack.no-url");
            } else {
                plugin.messages().send(viewer, "pack.sent", "count", 1);
            }
        });
        action("Enviar a todos", Material.ENDER_EYE, c -> {
            int n = pm.sendAll();
            plugin.messages().send(viewer, n > 0 ? "pack.sent" : "pack.no-url", "count", n);
        });
        action("Ver validaciones en el chat", Material.WRITABLE_BOOK, c -> {
            viewer.closeInventory();
            for (String line : pm.report()) {
                viewer.sendMessage(Text.mm(line));
            }
        }, "Errores y avisos de la última construcción");
        info("Carpeta de modelos", Material.CHEST, "plugins/DimensionForge/pack/models/<tipo>/<id>.bbmodel",
                "Tipos: ore (menas), item (ítems)", "Archivos extra: pack/extra/");
    }
}
