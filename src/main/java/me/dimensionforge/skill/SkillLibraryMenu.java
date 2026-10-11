package me.dimensionforge.skill;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.skill.model.SkillDef;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Biblioteca de habilidades con filtros, búsqueda y orden. Con "picker" sirve para asignar una habilidad. */
public final class SkillLibraryMenu extends PagedMenu<SkillDef> {

    private final Consumer<SkillDef> picker;
    final SkillFilters filters = new SkillFilters();

    public SkillLibraryMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, Consumer<SkillDef> picker) {
        super(plugin, viewer, parent);
        this.picker = picker;
    }

    public static Material iconOf(SkillDef d) {
        return switch (d.element) {
            case FIRE -> Material.BLAZE_POWDER;
            case ICE -> Material.SNOWBALL;
            case LIGHTNING -> Material.LIGHTNING_ROD;
            case POISON -> Material.SPIDER_EYE;
            case SHADOW -> Material.ECHO_SHARD;
            case LIGHT -> Material.GLOWSTONE_DUST;
            case EARTH -> Material.CLAY_BALL;
            case WIND -> Material.FEATHER;
            case BLOOD -> Material.REDSTONE;
            case ARCANE -> Material.AMETHYST_SHARD;
        };
    }

    @Override
    protected String titleKey() {
        return "gui.skills-title";
    }

    @Override
    protected List<SkillDef> items() {
        List<SkillDef> out = new ArrayList<>();
        for (SkillDef d : plugin.skills().all()) {
            if (filters.passes(d)) {
                out.add(d);
            }
        }
        filters.sort(out);
        return out;
    }

    @Override
    protected boolean searchable() {
        return true;
    }

    @Override
    protected boolean matches(SkillDef d, String q) {
        return d.name.toLowerCase().contains(q) || d.id.contains(q) || d.description.toLowerCase().contains(q)
                || d.element.label.toLowerCase().contains(q) || d.effect.label.toLowerCase().contains(q);
    }

    @Override
    protected ItemStack render(SkillDef d) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + Text.escape(d.description));
        lore.add("");
        lore.add("<gray>Disparador: <white>" + d.trigger.label
                + (d.trigger.unit.isEmpty() ? "" : " (" + fmt(d.triggerValue) + " " + d.trigger.unit + ")"));
        lore.add("<gray>Forma: <white>" + d.shape.label + " <gray>Elemento: <white>" + d.element.label);
        lore.add("<gray>Efecto: <white>" + d.effect.label + (d.secondary != null ? " + " + d.secondary.label : ""));
        lore.add("<gray>Potencia: <white>" + fmt(d.power) + " <gray>Radio: <white>" + fmt(d.radius)
                + " <gray>Enfriamiento: <white>" + fmt(d.cooldown) + "s");
        lore.add("<gray>Nivel: <gold>" + "★".repeat(Math.max(1, d.tier)) + "<dark_gray>" + "★".repeat(Math.max(0, 5 - d.tier)));
        if (d.signature) {
            lore.add("<light_purple>Habilidad de firma");
        }
        lore.add("<dark_gray>id: " + d.id);
        lore.add("");
        lore.add(picker != null ? "<yellow>Click: <white>asignar al mob" : "<yellow>Click: <white>mostrar el id en el chat");
        return ItemBuilder.of(iconOf(d)).name("<aqua>" + Text.escape(d.name)).lore(lore).glow(d.signature).build();
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }

    @Override
    protected void onItemClick(SkillDef d, MenuClick c) {
        if (picker != null) {
            picker.accept(d);
        } else {
            viewer.sendMessage(Text.mm("<aqua>" + Text.escape(d.name) + " <gray>→ id: <white>" + d.id));
        }
    }

    @Override
    protected void bottom() {
        set(46, ItemBuilder.of(Material.HOPPER).name("<yellow>Filtros y orden")
                .lore("<gray>" + Text.escape(filters.summary()), "<gray>Orden: <white>" + filters.sort.label, "",
                        "<yellow>Click: <white>abrir filtros", "<yellow>Click der: <white>quitar filtros").build(), c -> {
            if (c.right()) {
                filters.reset();
                page = 0;
                refresh();
            } else {
                new SkillFilterMenu(plugin, viewer, this, filters).open();
            }
        });
        set(50, ItemBuilder.of(Material.BOOK).name("<aqua>Biblioteca")
                .lore("<gray>Habilidades totales: <white>" + plugin.skills().count(),
                        "<gray>Nombre, elemento, efecto o disparador", "<gray>sirven en el buscador").build());
    }
}
