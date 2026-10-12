package me.dimensionforge.mob;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.skill.SkillConfigMenu;
import me.dimensionforge.skill.SkillLibraryMenu;
import me.dimensionforge.skill.model.SkillBinding;
import me.dimensionforge.skill.model.SkillDef;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Lista de habilidades asignadas a un mob (o a una fase de jefe). */
public final class MobSkillsMenu extends PagedMenu<SkillBinding> {

    private final MobDef def;
    private final List<SkillBinding> list;
    private final String menuTitle;

    public MobSkillsMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, MobDef def, List<SkillBinding> list,
                         String menuTitle) {
        super(plugin, viewer, parent);
        this.def = def;
        this.list = list;
        this.menuTitle = menuTitle;
    }

    @Override
    protected String titleKey() {
        return "gui.settings-title";
    }

    @Override
    protected Component title() {
        return Text.mm(menuTitle);
    }

    @Override
    protected List<SkillBinding> items() {
        return new ArrayList<>(list);
    }

    @Override
    protected ItemStack render(SkillBinding b) {
        SkillDef base = plugin.skills().get(b.skillId);
        if (base == null) {
            return ItemBuilder.of(Material.BARRIER).name("<red>" + Text.escape(b.skillId))
                    .lore("<gray>La habilidad ya no existe en la biblioteca.", "", "<yellow>Shift+der: <white>quitar").build();
        }
        SkillDef eff = b.applyTo(base);
        return ItemBuilder.of(SkillLibraryMenu.iconOf(base)).name((b.enabled ? "<green>" : "<red>") + Text.escape(base.name))
                .lore("<gray>" + Text.escape(base.description),
                        "<gray>Disparador: <white>" + eff.trigger.label + (eff.trigger.unit.isEmpty() ? "" : " (" + eff.triggerValue + " " + eff.trigger.unit + ")"),
                        "<gray>Potencia: <white>" + eff.power + " <gray>Radio: <white>" + eff.radius + " <gray>Enfriamiento: <white>" + eff.cooldown + "s",
                        "<gray>Personalizada: " + (b.hasOverrides() ? "<yellow>sí" : "<white>no"), "",
                        "<yellow>Click izq: <white>configurar", "<yellow>Click der: <white>activar/desactivar",
                        "<yellow>Shift+der: <white>quitar").glow(b.enabled).build();
    }

    @Override
    protected void onItemClick(SkillBinding b, MenuClick c) {
        if (c.right() && c.shift()) {
            list.remove(b);
            plugin.mobs().changed(def);
            refresh();
        } else if (c.right()) {
            b.enabled = !b.enabled;
            plugin.mobs().changed(def);
            refresh();
        } else if (c.left()) {
            new SkillConfigMenu(plugin, viewer, this, def, b).open();
        }
    }

    @Override
    protected void bottom() {
        set(50, ItemBuilder.of(Material.LIME_CONCRETE).name("<green>Añadir habilidad")
                .lore("<gray>Abre la biblioteca con filtros y búsqueda").build(),
                c -> new SkillLibraryMenu(plugin, viewer, this, picked -> {
                    list.add(new SkillBinding(picked.id));
                    plugin.mobs().changed(def);
                    plugin.messages().send(viewer, "skill.assigned", "skill", picked.name, "id", def.id);
                    this.open();
                }).open());
    }
}
