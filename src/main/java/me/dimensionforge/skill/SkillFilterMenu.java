package me.dimensionforge.skill;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import me.dimensionforge.skill.model.Effect;
import me.dimensionforge.skill.model.Element;
import me.dimensionforge.skill.model.Shape;
import me.dimensionforge.skill.model.Trigger;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Filtros de la biblioteca: categoría, elemento, disparador, forma, efecto, nivel, solo firma y orden. */
final class SkillFilterMenu extends SettingsMenu {

    private final SkillFilters f;

    SkillFilterMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, SkillFilters f) {
        super(plugin, viewer, parent, "<dark_gray>Filtros de habilidades");
        this.f = f;
    }

    /** Rota por los valores de un enum; null significa "cualquiera". */
    private <E extends Enum<E>> void rotate(String name, Material mat, E[] values, Supplier<E> get, Consumer<E> set,
                                            Function<E, String> label) {
        E cur = get.get();
        action(name + ": " + (cur == null ? "cualquiera" : label.apply(cur)), mat, c -> {
            int idx = -1;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == get.get()) {
                    idx = i;
                }
            }
            int next = c.right() ? idx - 1 : idx + 1;
            if (next >= values.length) {
                next = -1;
            }
            if (next < -1) {
                next = values.length - 1;
            }
            set.accept(next == -1 ? null : values[next]);
            refresh();
        }, "Click izq: siguiente", "Click der: anterior");
    }

    @Override
    protected void define() {
        rotate("Categoría", Material.COMPASS, Effect.Category.values(), () -> f.category, v -> f.category = v, v -> v.label);
        rotate("Elemento", Material.BLAZE_POWDER, Element.values(), () -> f.element, v -> f.element = v, v -> v.label);
        rotate("Disparador", Material.TRIPWIRE_HOOK, Trigger.values(), () -> f.trigger, v -> f.trigger = v, v -> v.label);
        rotate("Forma", Material.ARROW, Shape.values(), () -> f.shape, v -> f.shape = v, v -> v.label);
        rotate("Efecto", Material.POTION, Effect.values(), () -> f.effect, v -> f.effect = v, v -> v.label);
        intVal("Nivel mínimo", Material.GOLD_NUGGET, () -> f.minTier, v -> f.minTier = v, 1, 0, 5, "0 = cualquiera");
        bool("Solo habilidades de firma", Material.NETHER_STAR, () -> f.signatureOnly, v -> f.signatureOnly = v);
        cycle("Orden", Material.COMPARATOR, () -> f.sort, v -> f.sort = v, SkillFilters.Sort.values());
        action("Quitar todos los filtros", Material.BARRIER, c -> {
            f.reset();
            refresh();
        });
    }
}
