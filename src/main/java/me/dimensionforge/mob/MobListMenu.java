package me.dimensionforge.mob;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.ConfirmMenu;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.MenuClick;
import me.dimensionforge.gui.PagedMenu;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Mobs custom de una dimensión. */
public final class MobListMenu extends PagedMenu<MobDef> {

    private final String dimension;

    public MobListMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, String dimension) {
        super(plugin, viewer, parent);
        this.dimension = dimension;
    }

    @Override
    protected String titleKey() {
        return "gui.mobs-title";
    }

    @Override
    protected List<MobDef> items() {
        return plugin.mobs().forDimension(dimension);
    }

    @Override
    protected boolean searchable() {
        return true;
    }

    @Override
    protected boolean matches(MobDef item, String query) {
        return item.id.contains(query) || item.entityType.toLowerCase().contains(query);
    }

    @Override
    protected ItemStack render(MobDef m) {
        Material egg = Material.matchMaterial(m.entityType + "_SPAWN_EGG");
        return ItemBuilder.of(egg == null ? Material.ZOMBIE_SPAWN_EGG : egg).name((m.enabled ? "<green>" : "<red>") + m.id)
                .lore("<gray>Nombre: <white>" + Text.escape(m.displayName),
                        "<gray>Tipo base: <white>" + m.entityType,
                        "<gray>Vida: <white>" + m.health + " <gray>Daño: <white>" + m.damage,
                        "<gray>Modelo: <white>" + (m.hasModel() ? Text.escape(m.model) : "ninguno (modo simple)"),
                        "<gray>Habilidades: <white>" + m.skills.size() + " <gray>Fases: <white>" + m.phases.size(), "",
                        "<yellow>Click izq: <white>editar", "<yellow>Click der: <white>aparecer aquí",
                        "<yellow>Shift+der: <white>borrar").glow(m.bossBar).build();
    }

    @Override
    protected void onItemClick(MobDef m, MenuClick c) {
        if (c.right() && c.shift()) {
            new ConfirmMenu(plugin, viewer, "<red>¿Borrar el mob " + m.id + "?",
                    List.of("<gray>Se eliminan también los mobs vivos", "<gray>de este tipo y sus modelos."), () -> {
                plugin.mobs().remove(m);
                plugin.messages().send(viewer, "mob.removed", "id", m.id);
                new MobListMenu(plugin, viewer, parent, dimension).open();
            }, this::open).open();
        } else if (c.right()) {
            if (plugin.mobs().spawn(m, viewer.getLocation()) != null) {
                plugin.messages().send(viewer, "mob.spawned", "id", m.id);
            } else {
                plugin.messages().send(viewer, "mob.spawn-failed", "id", m.id);
            }
        } else if (c.left()) {
            new MobEditMenu(plugin, viewer, this, m).open();
        }
    }

    @Override
    protected void bottom() {
        set(50, ItemBuilder.of(Material.LIME_CONCRETE).name("<green>Crear mob").lore("<gray>Pide un id y el tipo base").build(),
                c -> plugin.textInput().request(viewer, "Id del mob (a-z, 0-9, _ -)", "", id -> {
                    String clean = id.toLowerCase().trim();
                    if (!clean.matches("[a-z0-9_-]{1,32}") || plugin.mobs().get(clean) != null) {
                        plugin.messages().send(viewer, "error.mob-id-invalid", "id", id);
                        open();
                        return;
                    }
                    plugin.textInput().request(viewer, "Tipo base (ej. ZOMBIE, SKELETON, IRON_GOLEM)", "ZOMBIE", type -> {
                        EntityType t;
                        try {
                            t = EntityType.valueOf(type.trim().toUpperCase());
                        } catch (IllegalArgumentException ex) {
                            t = null;
                        }
                        if (t == null || MobManager.classOf(t) == null) {
                            plugin.messages().send(viewer, "error.mob-type-invalid", "value", type);
                            open();
                            return;
                        }
                        MobDef d = plugin.mobs().create(dimension, clean, t);
                        plugin.messages().send(viewer, "mob.created", "id", d.id);
                        new MobEditMenu(plugin, viewer, this, d).open();
                    }, this::open);
                }, this::open));
    }
}
