package me.dimensionforge.gui;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Menú de ajustes: cada ajuste es un ítem (booleano alterna, numérico con +/- y shift x10,
 * texto por chat/yunque). Las subclases definen sus ajustes en define().
 */
public abstract class SettingsMenu extends PagedMenu<SettingsMenu.Entry> {

    public record Entry(Supplier<ItemStack> icon, ClickHandler handler) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final String menuTitle;

    protected SettingsMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, String menuTitle) {
        super(plugin, viewer, parent);
        this.menuTitle = menuTitle;
    }

    protected abstract void define();

    /** Se llama tras cada edición (guardar/aplicar). */
    protected void changed() {
    }

    @Override
    protected List<Entry> items() {
        entries.clear();
        define();
        return entries;
    }

    @Override
    protected ItemStack render(Entry item) {
        return item.icon().get();
    }

    @Override
    protected void onItemClick(Entry item, MenuClick click) {
        item.handler().onClick(click);
    }

    @Override
    protected String titleKey() {
        return "gui.settings-title";
    }

    @Override
    protected Component title() {
        return me.dimensionforge.util.Text.mm(menuTitle);
    }

    private void apply() {
        changed();
        refresh();
    }

    protected void bool(String name, Material mat, BooleanSupplier get, Consumer<Boolean> set, String... desc) {
        entries.add(new Entry(() -> {
            boolean v = get.getAsBoolean();
            return ItemBuilder.of(v ? Material.LIME_DYE : Material.GRAY_DYE).name("<yellow>" + name)
                    .lore(loreOf(name, mat, v ? "<green>Sí" : "<red>No", List.of("<gray>Click: alternar"), desc)).build();
        }, c -> {
            set.accept(!get.getAsBoolean());
            apply();
        }));
    }

    private List<String> loreOf(String name, Material mat, String value, List<String> hints, String[] desc) {
        List<String> lore = new ArrayList<>();
        if (value != null) {
            lore.add("<gray>Valor: <white>" + value);
        }
        for (String d : desc) {
            lore.add("<dark_gray>" + d);
        }
        lore.addAll(hints);
        return lore;
    }

    protected void intVal(String name, Material mat, IntSupplier get, IntConsumer set, int step, int min, int max, String... desc) {
        List<String> hints = List.of("<gray>Click izq: +" + step, "<gray>Click der: -" + step,
                "<gray>Shift: x10 · Q: escribir valor");
        entries.add(new Entry(() -> ItemBuilder.of(mat).name("<yellow>" + name)
                .lore(loreOf(name, mat, String.valueOf(get.getAsInt()), hints, desc)).build(), c -> {
            if (c.drop()) {
                plugin.textInput().request(viewer, "Valor de " + name, String.valueOf(get.getAsInt()), text -> {
                    try {
                        set.accept(clampInt(Integer.parseInt(text.trim()), min, max));
                        changed();
                    } catch (NumberFormatException ex) {
                        plugin.messages().send(viewer, "error.not-number", "value", text);
                    }
                    open();
                }, this::open);
                return;
            }
            int delta = step * (c.shift() ? 10 : 1) * (c.left() ? 1 : c.right() ? -1 : 0);
            if (delta != 0) {
                set.accept(clampInt(get.getAsInt() + delta, min, max));
                apply();
            }
        }));
    }

    protected void dbl(String name, Material mat, DoubleSupplier get, DoubleConsumer set, double step, double min, double max, String... desc) {
        List<String> hints = List.of("<gray>Click izq: +" + fmt(step), "<gray>Click der: -" + fmt(step),
                "<gray>Shift: x10 · Q: escribir valor");
        entries.add(new Entry(() -> ItemBuilder.of(mat).name("<yellow>" + name)
                .lore(loreOf(name, mat, fmt(get.getAsDouble()), hints, desc)).build(), c -> {
            if (c.drop()) {
                plugin.textInput().request(viewer, "Valor de " + name, fmt(get.getAsDouble()), text -> {
                    try {
                        set.accept(clampDouble(Double.parseDouble(text.trim().replace(',', '.')), min, max));
                        changed();
                    } catch (NumberFormatException ex) {
                        plugin.messages().send(viewer, "error.not-number", "value", text);
                    }
                    open();
                }, this::open);
                return;
            }
            double delta = step * (c.shift() ? 10 : 1) * (c.left() ? 1 : c.right() ? -1 : 0);
            if (delta != 0) {
                set.accept(clampDouble(get.getAsDouble() + delta, min, max));
                apply();
            }
        }));
    }

    protected <E extends Enum<E>> void cycle(String name, Material mat, Supplier<E> get, Consumer<E> set, E[] values, String... desc) {
        List<String> hints = List.of("<gray>Click izq: siguiente", "<gray>Click der: anterior");
        entries.add(new Entry(() -> ItemBuilder.of(mat).name("<yellow>" + name)
                .lore(loreOf(name, mat, String.valueOf(get.get()), hints, desc)).build(), c -> {
            int idx = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == get.get()) {
                    idx = i;
                }
            }
            idx = c.right() ? (idx - 1 + values.length) % values.length : (idx + 1) % values.length;
            set.accept(values[idx]);
            apply();
        }));
    }

    protected void str(String name, Material mat, Supplier<String> get, Consumer<String> set, String... desc) {
        List<String> hints = List.of("<gray>Click izq: escribir", "<gray>Click der: borrar");
        entries.add(new Entry(() -> {
            String v = get.get();
            return ItemBuilder.of(mat).name("<yellow>" + name)
                    .lore(loreOf(name, mat, v == null || v.isEmpty() ? "<red>(vacío)" : me.dimensionforge.util.Text.escape(v), hints, desc)).build();
        }, c -> {
            if (c.right()) {
                set.accept("");
                apply();
                return;
            }
            plugin.textInput().request(viewer, name, get.get() == null ? "" : get.get(), text -> {
                set.accept(text);
                changed();
                open();
            }, this::open);
        }));
    }

    protected void action(String name, Material mat, ClickHandler handler, String... desc) {
        entries.add(new Entry(() -> ItemBuilder.of(mat).name("<yellow>" + name)
                .lore(loreOf(name, mat, null, List.of(), desc)).build(), handler));
    }

    protected void info(String name, Material mat, String... desc) {
        entries.add(new Entry(() -> ItemBuilder.of(mat).name("<yellow>" + name)
                .lore(loreOf(name, mat, null, List.of(), desc)).build(), c -> {
        }));
    }

    protected static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    protected static double clampDouble(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    protected static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1.0E9) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
