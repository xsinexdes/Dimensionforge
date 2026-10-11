package me.dimensionforge.gui;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Menú de confirmación sí/no. */
public final class ConfirmMenu extends Menu {

    private final String titleText;
    private final List<String> lore;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmMenu(DimensionForgePlugin plugin, Player viewer, String titleText, List<String> lore,
                       Runnable onConfirm, Runnable onCancel) {
        super(plugin, viewer, null);
        this.titleText = titleText;
        this.lore = lore;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected Component title() {
        return me.dimensionforge.util.Text.mm(titleText);
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void build() {
        set(11, ItemBuilder.of(Material.LIME_CONCRETE).name(plugin.messages().raw("gui.confirm")).build(), c -> onConfirm.run());
        set(13, ItemBuilder.of(Material.PAPER).name(titleText).lore(lore).build());
        set(15, ItemBuilder.of(Material.RED_CONCRETE).name(plugin.messages().raw("gui.cancel")).build(), c -> {
            if (onCancel != null) {
                onCancel.run();
            } else {
                viewer.closeInventory();
            }
        });
        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }
}
