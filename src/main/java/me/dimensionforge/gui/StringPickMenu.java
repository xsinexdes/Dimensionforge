package me.dimensionforge.gui;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Selector paginado con buscador para elegir un texto de una lista (animaciones, tipos, biomas...). */
public final class StringPickMenu extends PagedMenu<String> {

    private final String menuTitle;
    private final Supplier<List<String>> options;
    private final Material icon;
    private final Consumer<String> onPick;
    private final String current;

    public StringPickMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, String menuTitle,
                          Supplier<List<String>> options, Material icon, String current, Consumer<String> onPick) {
        super(plugin, viewer, parent);
        this.menuTitle = menuTitle;
        this.options = options;
        this.icon = icon;
        this.current = current == null ? "" : current;
        this.onPick = onPick;
    }

    @Override
    protected String titleKey() {
        return "gui.settings-title";
    }

    @Override
    protected Component title() {
        return Text.mm(menuTitle);
    }

    @Override
    protected List<String> items() {
        return options.get();
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
    protected ItemStack render(String item) {
        boolean sel = item.equals(current);
        return ItemBuilder.of(icon).name((sel ? "<green>" : "<yellow>") + Text.escape(item))
                .lore("<gray>Click: elegir").glow(sel).build();
    }

    @Override
    protected void onItemClick(String item, MenuClick click) {
        onPick.accept(item);
    }
}
