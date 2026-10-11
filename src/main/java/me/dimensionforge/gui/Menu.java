package me.dimensionforge.gui;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Menú base: inventario con botones clicables. */
public abstract class Menu implements InventoryHolder {

    protected final DimensionForgePlugin plugin;
    protected final Player viewer;
    protected final Menu parent;
    private Inventory inventory;
    private final Map<Integer, ClickHandler> handlers = new HashMap<>();
    private boolean switching;

    protected Menu(DimensionForgePlugin plugin, Player viewer, Menu parent) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.parent = parent;
    }

    protected abstract Component title();

    protected abstract int rows();

    /** Rellena los botones usando set(...). */
    protected abstract void build();

    /** Se llama al cerrar el menú (no cuando se cambia a otro menú propio). */
    protected void onClose() {
    }

    public void open() {
        render(true);
        switching = true;
        viewer.openInventory(inventory);
        switching = false;
    }

    /** Redibuja el contenido sin reabrir. */
    public void refresh() {
        if (inventory == null) {
            return;
        }
        render(false);
    }

    private void render(boolean forceNew) {
        handlers.clear();
        if (forceNew || inventory == null || inventory.getSize() != rows() * 9) {
            inventory = Bukkit.createInventory(this, rows() * 9, title());
        } else {
            inventory.clear();
        }
        build();
    }

    protected void set(int slot, ItemStack item, ClickHandler handler) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (handler != null) {
            handlers.put(slot, handler);
        }
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void fillEmpty(Material filler) {
        ItemStack pane = ItemBuilder.of(filler).name(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, pane);
            }
        }
    }

    /** Botón "volver": abre el menú padre o cierra. */
    protected void backButton(int slot) {
        set(slot, ItemBuilder.of(Material.ARROW).name(plugin.messages().raw("gui.back")).build(), c -> {
            if (parent != null) {
                parent.open();
            } else {
                viewer.closeInventory();
            }
        });
    }

    void handleClick(int slot, MenuClick click) {
        ClickHandler h = handlers.get(slot);
        if (h != null) {
            try {
                h.onClick(click);
            } catch (Exception ex) {
                plugin.getLogger().warning("Error en menú: " + ex);
                plugin.messages().send(viewer, "error.generic", "error", String.valueOf(ex.getMessage()));
            }
        }
    }

    void handleClose() {
        if (!switching) {
            onClose();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
