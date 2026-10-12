package me.dimensionforge.portal;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.Fx;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Hacha de Portales: selección de regiones estilo WorldEdit. */
public final class PortalWand {

    public static final class Selection {
        public String world;
        public int[] p1;
        public int[] p2;

        public boolean complete() {
            return p1 != null && p2 != null;
        }
    }

    private final DimensionForgePlugin plugin;
    private final Map<UUID, Selection> selections = new HashMap<>();

    public PortalWand(DimensionForgePlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack create() {
        String matName = plugin.getConfig().getString("wand.material", "GOLDEN_AXE");
        Material m = Material.matchMaterial(matName);
        if (m == null) {
            m = Material.GOLDEN_AXE;
        }
        int cmd = plugin.getConfig().getInt("wand.custom-model-data", 0);
        ItemStack item = ItemBuilder.of(m)
                .name(plugin.getConfig().getString("wand.name", "<light_purple><bold>Hacha de Portales"))
                .lore(plugin.getConfig().getStringList("wand.lore"))
                .cmd(cmd > 0 ? cmd : null).glow(true).build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.WAND, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(Keys.WAND, PersistentDataType.BYTE);
    }

    public Selection get(Player p) {
        return selections.get(p.getUniqueId());
    }

    public Selection getOrCreate(Player p) {
        return selections.computeIfAbsent(p.getUniqueId(), k -> new Selection());
    }

    public void clear(Player p) {
        selections.remove(p.getUniqueId());
    }

    public void forget(UUID id) {
        selections.remove(id);
    }

    public void setPos(Player p, boolean first, Location loc) {
        Selection s = getOrCreate(p);
        String w = loc.getWorld().getName();
        if (s.world != null && !s.world.equals(w)) {
            s.p1 = null;
            s.p2 = null;
        }
        s.world = w;
        int[] pos = {loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()};
        if (first) {
            s.p1 = pos;
        } else {
            s.p2 = pos;
        }
        plugin.messages().send(p, first ? "wand.pos1" : "wand.pos2", "x", pos[0], "y", pos[1], "z", pos[2]);
        if (s.complete()) {
            int sx = Math.abs(s.p1[0] - s.p2[0]) + 1;
            int sy = Math.abs(s.p1[1] - s.p2[1]) + 1;
            int sz = Math.abs(s.p1[2] - s.p2[2]) + 1;
            plugin.messages().send(p, "wand.size", "sx", sx, "sy", sy, "sz", sz, "vol", (long) sx * sy * sz);
        }
    }

    /** Dibuja los bordes de cada selección, solo para su dueño. */
    public void tickParticles() {
        Particle part = Fx.particle(plugin.getConfig().getString("wand.particle", "DUST"), Particle.DUST);
        Color color = Fx.color(plugin.getConfig().getString("wand.color", "#FF55FF"), Color.FUCHSIA);
        for (Map.Entry<UUID, Selection> e : selections.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            Selection s = e.getValue();
            if (p == null || s.world == null || !p.getWorld().getName().equals(s.world)) {
                continue;
            }
            if (!plugin.wand().isWand(p.getInventory().getItemInMainHand())) {
                continue;
            }
            if (s.p1 != null && s.p2 == null) {
                Fx.spawnFor(p, new Location(p.getWorld(), s.p1[0] + 0.5, s.p1[1] + 0.5, s.p1[2] + 0.5), part, 3, 0.2, color);
            } else if (s.p2 != null && s.p1 == null) {
                Fx.spawnFor(p, new Location(p.getWorld(), s.p2[0] + 0.5, s.p2[1] + 0.5, s.p2[2] + 0.5), part, 3, 0.2, color);
            } else if (s.complete()) {
                drawBox(p, s, part, color);
            }
        }
    }

    private void drawBox(Player p, Selection s, Particle part, Color color) {
        double x1 = Math.min(s.p1[0], s.p2[0]);
        double y1 = Math.min(s.p1[1], s.p2[1]);
        double z1 = Math.min(s.p1[2], s.p2[2]);
        double x2 = Math.max(s.p1[0], s.p2[0]) + 1;
        double y2 = Math.max(s.p1[1], s.p2[1]) + 1;
        double z2 = Math.max(s.p1[2], s.p2[2]) + 1;
        double longest = Math.max(x2 - x1, Math.max(y2 - y1, z2 - z1));
        double step = Math.max(0.5, longest / 24.0);
        for (int xi = 0; xi < 2; xi++) {
            for (int yi = 0; yi < 2; yi++) {
                double x = xi == 0 ? x1 : x2;
                double y = yi == 0 ? y1 : y2;
                for (double z = z1; z <= z2; z += step) {
                    Fx.spawnFor(p, new Location(p.getWorld(), x, y, z), part, 1, 0, color);
                }
            }
        }
        for (int xi = 0; xi < 2; xi++) {
            for (int zi = 0; zi < 2; zi++) {
                double x = xi == 0 ? x1 : x2;
                double z = zi == 0 ? z1 : z2;
                for (double y = y1; y <= y2; y += step) {
                    Fx.spawnFor(p, new Location(p.getWorld(), x, y, z), part, 1, 0, color);
                }
            }
        }
        for (int yi = 0; yi < 2; yi++) {
            for (int zi = 0; zi < 2; zi++) {
                double y = yi == 0 ? y1 : y2;
                double z = zi == 0 ? z1 : z2;
                for (double x = x1; x <= x2; x += step) {
                    Fx.spawnFor(p, new Location(p.getWorld(), x, y, z), part, 1, 0, color);
                }
            }
        }
    }
}
