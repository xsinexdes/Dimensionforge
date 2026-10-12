package me.dimensionforge.mob;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.skill.model.BossPhase;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fases de un jefe: cada una se activa al bajar de un porcentaje de vida. */
public final class BossPhasesMenu extends PagedMenu<BossPhase> {

    private final MobDef def;

    public BossPhasesMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, MobDef def) {
        super(plugin, viewer, parent);
        this.def = def;
    }

    @Override
    protected String titleKey() {
        return "gui.phases-title";
    }

    @Override
    protected List<BossPhase> items() {
        return new ArrayList<>(def.phases);
    }

    @Override
    protected ItemStack render(BossPhase p) {
        return ItemBuilder.of(Material.WITHER_SKELETON_SKULL).name("<gold>" + Text.escape(p.name))
                .lore("<gray>Se activa con vida ≤ <white>" + p.healthPercent + "%",
                        "<gray>Velocidad x<white>" + p.speedMultiplier + " <gray>Daño x<white>" + p.damageMultiplier,
                        "<gray>Habilidades propias: <white>" + p.skills.size()
                                + (p.replaceBaseSkills ? " <red>(reemplaza las base)" : ""), "",
                        "<yellow>Click izq: <white>editar", "<yellow>Shift+der: <white>borrar").build();
    }

    @Override
    protected void onItemClick(BossPhase p, MenuClick c) {
        if (c.right() && c.shift()) {
            def.phases.remove(p);
            plugin.mobs().changed(def);
            refresh();
        } else if (c.left()) {
            new PhaseEditMenu(plugin, viewer, this, def, p).open();
        }
    }

    @Override
    protected void bottom() {
        set(50, ItemBuilder.of(Material.LIME_CONCRETE).name("<green>Añadir fase")
                .lore("<gray>Por defecto: vida ≤ 50%").build(), c -> {
            BossPhase p = new BossPhase();
            p.healthPercent = def.phases.isEmpty() ? 66 : Math.max(5, def.phases.get(def.phases.size() - 1).healthPercent - 30);
            p.name = "Fase " + (def.phases.size() + 2);
            def.phases.add(p);
            sort();
            plugin.mobs().changed(def);
            new PhaseEditMenu(plugin, viewer, this, def, p).open();
        });
    }

    /** Las fases se mantienen de mayor a menor porcentaje. */
    void sort() {
        def.phases.sort(Comparator.comparingInt((BossPhase p) -> p.healthPercent).reversed());
    }
}
