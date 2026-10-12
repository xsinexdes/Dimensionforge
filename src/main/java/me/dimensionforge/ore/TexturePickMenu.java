package me.dimensionforge.ore;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Selector de texturas de mena: lista solas las que hay en pack/textures/ore/ (y los .bbmodel de models/ore).
 * Si el pack ya está construido y el jugador lo tiene, cada elemento muestra el bloque real.
 */
public final class TexturePickMenu extends PagedMenu<String> {

    private final OreDef ore;
    private final Runnable onChange;

    public TexturePickMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, OreDef ore, Runnable onChange) {
        super(plugin, viewer, parent);
        this.ore = ore;
        this.onChange = onChange;
    }

    @Override
    protected String titleKey() {
        return "gui.settings-title";
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_gray>Textura de " + Text.escape(ore.id));
    }

    @Override
    protected List<String> items() {
        return plugin.packs().availableOreModels();
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
    protected ItemStack render(String id) {
        boolean sel = ore.appearance == OreDef.Appearance.MODEL && ore.modelId().equals(id);
        Integer cmd = plugin.packs().cmds().find("ore", id);
        ItemBuilder b = ItemBuilder.of(cmd == null ? Material.ITEM_FRAME : plugin.packs().cmds().baseMaterial())
                .name((sel ? "<green>" : "<yellow>") + Text.escape(id));
        if (cmd != null) {
            b.cmd(cmd);
        }
        b.lore(cmd == null ? "<red>Nueva: pulsa «Reconstruir pack» para verla" : "<gray>Click: usar esta textura").glow(sel);
        return b.build();
    }

    @Override
    protected void onItemClick(String id, MenuClick click) {
        ore.model = id;
        ore.appearance = OreDef.Appearance.MODEL;
        finish();
    }

    @Override
    protected void bottom() {
        set(46, ItemBuilder.of(Material.CHEST).name("<yellow>¿Dónde meto mis PNG?")
                .lore("<gray>plugins/DimensionForge/pack/textures/ore/",
                        "<gray>Un PNG por mena: <white>mithril.png",
                        "<gray>Caras distintas: <white>_side, _top, _bottom",
                        "<gray>Luego pulsa «Reconstruir pack».").build());
        set(50, ItemBuilder.of(Material.ANVIL).name("<yellow>Reconstruir pack")
                .lore("<gray>Detecta PNG nuevos y envía el pack a todos.").build(), c -> {
            plugin.messages().send(viewer, "pack.building");
            plugin.packs().build(viewer, () -> {
                plugin.packs().sendAll();
                refresh();
            });
        });
        set(51, ItemBuilder.of(Material.BARRIER).name("<red>Sin textura (solo bloque base)").build(), c -> {
            ore.model = "";
            ore.appearance = OreDef.Appearance.BLOCK;
            finish();
        });
    }

    /** Guarda, reaplica los modelos en el mundo y vuelve al menú de la mena. */
    private void finish() {
        onChange.run();
        if (parent != null) {
            parent.open();
        } else {
            viewer.closeInventory();
        }
    }
}
