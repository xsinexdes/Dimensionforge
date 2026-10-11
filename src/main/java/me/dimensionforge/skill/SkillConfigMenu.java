package me.dimensionforge.skill;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import me.dimensionforge.gui.StringPickMenu;
import me.dimensionforge.mob.MobDef;
import me.dimensionforge.skill.model.SkillBinding;
import me.dimensionforge.skill.model.SkillDef;
import me.dimensionforge.skill.model.Trigger;
import me.dimensionforge.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Configuración de una habilidad asignada a un mob: valores propios, prueba y reinicio. */
public final class SkillConfigMenu extends SettingsMenu {

    private final MobDef owner;
    private final SkillBinding b;

    public SkillConfigMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, MobDef owner, SkillBinding b) {
        super(plugin, viewer, parent, "<dark_gray>Habilidad: <white>" + Text.escape(nameOf(plugin, b)));
        this.owner = owner;
        this.b = b;
    }

    private static String nameOf(DimensionForgePlugin plugin, SkillBinding b) {
        SkillDef d = plugin.skills().get(b.skillId);
        return d == null ? b.skillId : d.name;
    }

    @Override
    protected void changed() {
        plugin.mobs().changed(owner);
    }

    @Override
    protected void define() {
        SkillDef base = plugin.skills().get(b.skillId);
        if (base == null) {
            info("Habilidad no encontrada", Material.BARRIER, "Id: " + b.skillId);
            return;
        }
        SkillDef eff = b.applyTo(base);
        info(base.name, SkillLibraryMenu.iconOf(base), base.description, "Forma: " + base.shape.label,
                "Elemento: " + base.element.label, "Efecto: " + base.effect.label);
        bool("Activa", Material.LEVER, () -> b.enabled, v -> b.enabled = v);
        action("Disparador: " + eff.trigger.label + (b.trigger != null ? " (propio)" : ""), Material.TRIPWIRE_HOOK, c -> {
            Trigger[] all = Trigger.values();
            int idx = b.trigger == null ? -1 : b.trigger.ordinal();
            int next = c.right() ? idx - 1 : idx + 1;
            if (next >= all.length) {
                next = -1;
            }
            if (next < -1) {
                next = all.length - 1;
            }
            b.trigger = next == -1 ? null : all[next];
            b.triggerValue = null;
            changed();
            refresh();
        }, "Click izq: siguiente · der: anterior", "Original: " + base.trigger.label);
        if (!eff.trigger.unit.isEmpty()) {
            dbl("Valor del disparador (" + eff.trigger.unit + ")", Material.CLOCK, () -> b.applyTo(base).triggerValue,
                    v -> b.triggerValue = v, 1, 0, 600);
        }
        dbl("Potencia", Material.IRON_SWORD, () -> b.applyTo(base).power, v -> b.power = v, 1, 0.5, 100,
                "Daño en vida o intensidad del efecto");
        dbl("Radio", Material.COMPASS, () -> b.applyTo(base).radius, v -> b.radius = v, 0.5, 1, 20);
        dbl("Enfriamiento (s)", Material.CLOCK, () -> b.applyTo(base).cooldown, v -> b.cooldown = v, 1, 1, 300);
        dbl("Probabilidad (0-1)", Material.GOLD_NUGGET, () -> b.applyTo(base).chance, v -> b.chance = v, 0.05, 0.01, 1);
        action("Animación al lanzar: " + (b.animation.isBlank() ? "(ninguna)" : Text.escape(b.animation)), Material.JUKEBOX, c -> {
            List<String> opts = new ArrayList<>();
            opts.add("(ninguna)");
            opts.addAll(plugin.models().animations(owner.model));
            new StringPickMenu(plugin, viewer, this, "<dark_gray>Animación de la habilidad", () -> opts, Material.JUKEBOX,
                    b.animation, pick -> {
                b.animation = pick.equals("(ninguna)") ? "" : pick;
                changed();
                this.open();
            }).open();
        }, "Animación del modelo del mob");
        action("Probar ahora", Material.FIREWORK_ROCKET, c -> {
            viewer.closeInventory();
            if (plugin.skills().test(viewer, owner, b)) {
                plugin.messages().send(viewer, "skill.test-ok", "skill", base.name);
            } else {
                plugin.messages().send(viewer, "skill.test-failed", "skill", base.name);
            }
        }, "Usa un mob de este tipo cercano o crea uno temporal", "(sin IA, desaparece a los 12 s)");
        action("Reiniciar a valores originales", Material.BARRIER, c -> {
            b.reset();
            changed();
            refresh();
        });
    }
}
