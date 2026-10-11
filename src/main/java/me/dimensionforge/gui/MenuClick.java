package me.dimensionforge.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Datos de un click en un menú. */
public record MenuClick(Player player, ClickType type) {

    public boolean left() {
        return type == ClickType.LEFT || type == ClickType.SHIFT_LEFT;
    }

    public boolean right() {
        return type == ClickType.RIGHT || type == ClickType.SHIFT_RIGHT;
    }

    public boolean shift() {
        return type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT;
    }

    public boolean drop() {
        return type == ClickType.DROP || type == ClickType.CONTROL_DROP;
    }

    public boolean middle() {
        return type == ClickType.MIDDLE;
    }
}
