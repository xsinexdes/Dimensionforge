package me.dimensionforge.mob;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import me.dimensionforge.gui.StringPickMenu;
import me.dimensionforge.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Animación del modelo asociada a cada estado del mob. */
public final class MobAnimationsMenu extends SettingsMenu {

    private final MobDef d;

    public MobAnimationsMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, MobDef d) {
        super(plugin, viewer, parent, "<dark_gray>Animaciones de " + d.id);
        this.d = d;
    }

    @Override
    protected void changed() {
        plugin.mobs().changed(d);
    }

    private void slot(String label, Material mat, Supplier<String> get, Consumer<String> set) {
        String cur = get.get();
        action(label + ": " + (cur.isBlank() ? "(ninguna)" : Text.escape(cur)), mat, c -> {
            if (c.right()) {
                set.accept("");
                changed();
                refresh();
                return;
            }
            Supplier<List<String>> opts = () -> {
                List<String> l = new ArrayList<>();
                l.add("(ninguna)");
                l.addAll(plugin.models().animations(d.model));
                return l;
            };
            new StringPickMenu(plugin, viewer, this, "<dark_gray>Animación: " + label, opts, Material.JUKEBOX, cur, pick -> {
                set.accept(pick.equals("(ninguna)") ? "" : pick);
                changed();
                this.open();
            }).open();
        }, "Click izq: elegir de las animaciones del modelo", "Click der: quitar",
                d.hasModel() ? "Modelo: " + Text.escape(d.model) : "Primero elige un modelo");
    }

    @Override
    protected void define() {
        slot("Quieto (idle)", Material.PLAYER_HEAD, () -> d.animIdle, v -> d.animIdle = v);
        slot("Caminar", Material.LEATHER_BOOTS, () -> d.animWalk, v -> d.animWalk = v);
        slot("Atacar", Material.IRON_SWORD, () -> d.animAttack, v -> d.animAttack = v);
        slot("Recibir daño", Material.REDSTONE, () -> d.animHurt, v -> d.animHurt = v);
        slot("Muerte", Material.SKELETON_SKULL, () -> d.animDeath, v -> d.animDeath = v);
        slot("Aparición", Material.EGG, () -> d.animSpawn, v -> d.animSpawn = v);
        info("Cómo funciona", Material.BOOK, "idle/caminar se alternan solos según el movimiento.",
                "Ataque y daño se reproducen una vez al ocurrir.",
                "La muerte usa un modelo temporal y no deja entidades.");
    }
}
