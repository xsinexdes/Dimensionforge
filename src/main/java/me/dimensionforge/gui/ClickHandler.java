package me.dimensionforge.gui;

/** Acción al hacer click sobre un botón de menú. */
@FunctionalInterface
public interface ClickHandler {
    void onClick(MenuClick click);
}
