package me.dimensionforge.gui;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Menú paginado de 6 filas: 45 elementos + fila inferior de navegación. */
public abstract class PagedMenu<T> extends Menu {

    protected int page;
    protected String search = "";

    protected PagedMenu(DimensionForgePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    protected abstract List<T> items();

    protected abstract ItemStack render(T item);

    protected abstract void onItemClick(T item, MenuClick click);

    /** Coincidencia con el buscador. */
    protected boolean matches(T item, String query) {
        return true;
    }

    protected boolean searchable() {
        return false;
    }

    /** Botones extra en los slots 46,47,50,51,52 (el 45/53 son paginación, 48 volver, 49 info). */
    protected void bottom() {
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void build() {
        List<T> all = new ArrayList<>();
        for (T t : items()) {
            if (search.isEmpty() || matches(t, search.toLowerCase())) {
                all.add(t);
            }
        }
        int pages = Math.max(1, (all.size() + 44) / 45);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }
        int start = page * 45;
        for (int i = 0; i < 45 && start + i < all.size(); i++) {
            T item = all.get(start + i);
            set(i, render(item), c -> onItemClick(item, c));
        }
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            set(i, filler);
        }
        final int fp = pages;
        if (page > 0) {
            set(45, ItemBuilder.of(Material.ARROW).name(plugin.messages().raw("gui.prev")).build(), c -> {
                page--;
                refresh();
            });
        }
        if (page < fp - 1) {
            set(53, ItemBuilder.of(Material.ARROW).name(plugin.messages().raw("gui.next")).build(), c -> {
                page++;
                refresh();
            });
        }
        set(49, ItemBuilder.of(Material.PAPER).name(plugin.messages().raw("gui.page-info", "page", page + 1, "pages", pages, "total", all.size())).build());
        backButton(48);
        if (searchable()) {
            set(47, ItemBuilder.of(Material.NAME_TAG).name(plugin.messages().raw("gui.search"))
                    .lore(search.isEmpty() ? plugin.messages().raw("gui.search-none") : plugin.messages().raw("gui.search-current", "q", search),
                            plugin.messages().raw("gui.search-hint")).build(), c -> {
                if (c.right()) {
                    search = "";
                    page = 0;
                    refresh();
                } else {
                    plugin.textInput().request(viewer, plugin.messages().raw("input.search"), search, text -> {
                        search = text.trim();
                        page = 0;
                        open();
                    }, this::open);
                }
            });
        }
        bottom();
    }

    @Override
    protected Component title() {
        return plugin.messages().get(titleKey());
    }

    protected abstract String titleKey();
}
