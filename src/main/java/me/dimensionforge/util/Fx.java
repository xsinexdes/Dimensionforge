package me.dimensionforge.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/** Partículas, sonidos y efectos de poción resueltos por nombre (a prueba de cambios de nombre en la API). */
public final class Fx {

    private Fx() {
    }

    public static Particle particle(String name, Particle def) {
        if (name == null || name.isBlank()) {
            return def;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    public static Color color(String hex, Color def) {
        if (hex == null || hex.isBlank()) {
            return def;
        }
        try {
            String h = hex.trim();
            if (h.startsWith("#")) {
                h = h.substring(1);
            }
            int rgb = Integer.parseInt(h, 16);
            return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    /** Dibuja partículas en una ubicación (visible para todos los cercanos). */
    public static void spawn(Location loc, Particle p, int count, double spread, Color color) {
        World w = loc.getWorld();
        if (w == null || p == null) {
            return;
        }
        try {
            Object data = dataFor(p, color, 1.2f);
            if (data == null && p.getDataType() != Void.class) {
                return; // partícula que necesita datos que no sabemos dar
            }
            w.spawnParticle(p, loc, count, spread, spread, spread, data == null ? 0.01 : 0, data);
        } catch (IllegalArgumentException ex) {
            // partícula no válida en esta versión: se omite sin romper la habilidad
        }
    }

    /** Partícula solo para un jugador. */
    public static void spawnFor(Player player, Location loc, Particle p, int count, double spread, Color color) {
        if (p == null) {
            return;
        }
        try {
            Object data = dataFor(p, color, 1.0f);
            if (data == null && p.getDataType() != Void.class) {
                return;
            }
            player.spawnParticle(p, loc, count, spread, spread, spread, 0.0, data);
        } catch (IllegalArgumentException ex) {
            // se omite
        }
    }

    /** Datos extra que exigen algunas partículas (polvo, bloque, ítem). null si no necesita o no sabemos darlos. */
    private static Object dataFor(Particle p, Color color, float size) {
        Class<?> type = p.getDataType();
        if (type == Void.class) {
            return null;
        }
        if (type == Particle.DustOptions.class) {
            return new Particle.DustOptions(color == null ? Color.WHITE : color, size);
        }
        if (type == org.bukkit.block.data.BlockData.class) {
            return org.bukkit.Material.STONE.createBlockData();
        }
        if (type == org.bukkit.inventory.ItemStack.class) {
            return new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE);
        }
        if (type == Color.class) {
            return color == null ? Color.WHITE : color;
        }
        return null;
    }

    /** Reproduce un sonido por su clave (ej. "entity.zombie.hurt" o "mi_pack:boom"). */
    public static void sound(Location loc, String key, float volume, float pitch) {
        if (loc == null || loc.getWorld() == null || key == null || key.isBlank()) {
            return;
        }
        loc.getWorld().playSound(loc, key.trim(), SoundCategory.MASTER, volume, pitch);
    }

    public static void sound(Player player, String key, float volume, float pitch) {
        if (player == null || key == null || key.isBlank()) {
            return;
        }
        player.playSound(player.getLocation(), key.trim(), SoundCategory.MASTER, volume, pitch);
    }

    /** Efecto de poción por clave de registro (ej. "slowness", "mining_fatigue"). */
    public static PotionEffectType effect(String key) {
        if (key == null) {
            return null;
        }
        String k = key.trim().toLowerCase(Locale.ROOT);
        if (k.contains(":")) {
            k = k.substring(k.indexOf(':') + 1);
        }
        return PotionEffectType.getByKey(NamespacedKey.minecraft(k));
    }
}
