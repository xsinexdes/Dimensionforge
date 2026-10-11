package me.dimensionforge.mob;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import me.dimensionforge.gui.StringPickMenu;
import me.dimensionforge.skill.model.BossPhase;
import me.dimensionforge.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Edición de una fase de jefe. */
public final class PhaseEditMenu extends SettingsMenu {

    private final MobDef def;
    private final BossPhase p;

    public PhaseEditMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, MobDef def, BossPhase p) {
        super(plugin, viewer, parent, "<dark_gray>Fase: <white>" + Text.escape(p.name));
        this.def = def;
        this.p = p;
    }

    @Override
    protected void changed() {
        def.phases.sort(Comparator.comparingInt((BossPhase x) -> x.healthPercent).reversed());
        plugin.mobs().changed(def);
    }

    @Override
    protected void define() {
        str("Nombre de la fase", Material.NAME_TAG, () -> p.name, v -> p.name = v.isBlank() ? "Fase" : v);
        intVal("Se activa con vida ≤ (%)", Material.REDSTONE, () -> p.healthPercent, v -> p.healthPercent = v, 5, 1, 99);
        str("Mensaje al entrar", Material.OAK_SIGN, () -> p.message, v -> p.message = v, "MiniMessage; lo ven los jugadores a 48 bloques");
        str("Sonido al entrar", Material.NOTE_BLOCK, () -> p.sound, v -> p.sound = v.trim());
        action("Animación al entrar: " + (p.animation.isBlank() ? "(ninguna)" : Text.escape(p.animation)), Material.JUKEBOX, c -> {
            List<String> opts = new ArrayList<>();
            opts.add("(ninguna)");
            opts.addAll(plugin.models().animations(def.model));
            new StringPickMenu(plugin, viewer, this, "<dark_gray>Animación de fase", () -> opts, Material.JUKEBOX, p.animation, pick -> {
                p.animation = pick.equals("(ninguna)") ? "" : pick;
                changed();
                this.open();
            }).open();
        });
        dbl("Multiplicador de velocidad", Material.SUGAR, () -> p.speedMultiplier, v -> p.speedMultiplier = v, 0.1, 0.1, 5);
        dbl("Multiplicador de daño", Material.IRON_SWORD, () -> p.damageMultiplier, v -> p.damageMultiplier = v, 0.1, 0.1, 10);
        dbl("Curación al entrar (fracción)", Material.GOLDEN_APPLE, () -> p.healFraction, v -> p.healFraction = v, 0.05, 0, 1);
        bool("Reemplaza habilidades base", Material.BARRIER, () -> p.replaceBaseSkills, v -> p.replaceBaseSkills = v,
                "Si está activo, solo valen las de esta fase (y posteriores)");
        action("Habilidades de la fase (" + p.skills.size() + ")", Material.ENCHANTED_BOOK,
                c -> new MobSkillsMenu(plugin, viewer, this, def, p.skills, "<dark_gray>Habilidades: " + Text.escape(p.name)).open());
    }
}
