package me.dimensionforge.pack;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.ItemBuilder;
import me.dimensionforge.util.YamlIO;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Registro estable de CustomModelData (cmd_registry.yml): cada modelo (tipo + id) conserva su número aunque
 * se reconstruya el pack, se añadan o se quiten modelos. Los números nunca se reutilizan.
 */
public final class CmdRegistry {

    private final DimensionForgePlugin plugin;
    private final File file;
    /** tipo -> (id -> cmd) */
    private final Map<String, Map<String, Integer>> map = new LinkedHashMap<>();
    private int next;

    public CmdRegistry(DimensionForgePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cmd_registry.yml");
    }

    public synchronized void load() {
        map.clear();
        YamlConfiguration y = YamlIO.load(plugin, file);
        next = Math.max(plugin.getConfig().getInt("pack.cmd-start", 100000), y.getInt("next", 0));
        ConfigurationSection root = y.getConfigurationSection("models");
        if (root != null) {
            for (String type : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(type);
                if (s == null) {
                    continue;
                }
                Map<String, Integer> m = new TreeMap<>();
                for (String id : s.getKeys(false)) {
                    int v = s.getInt(id);
                    m.put(id, v);
                    next = Math.max(next, v + 1);
                }
                map.put(type, m);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next", next);
        for (Map.Entry<String, Map<String, Integer>> e : map.entrySet()) {
            for (Map.Entry<String, Integer> m : e.getValue().entrySet()) {
                y.set("models." + e.getKey() + "." + m.getKey(), m.getValue());
            }
        }
        YamlIO.saveAsync(plugin, file, y);
    }

    /** Devuelve el CMD de un modelo, asignando uno nuevo y estable si aún no tiene. */
    public synchronized int assign(String type, String id) {
        Map<String, Integer> m = map.computeIfAbsent(type, k -> new TreeMap<>());
        Integer v = m.get(id);
        if (v == null) {
            v = next++;
            m.put(id, v);
        }
        return v;
    }

    public synchronized Integer find(String type, String id) {
        Map<String, Integer> m = map.get(type);
        return m == null ? null : m.get(id);
    }

    public synchronized int size() {
        int n = 0;
        for (Map<String, Integer> m : map.values()) {
            n += m.size();
        }
        return n;
    }

    /** Copia ordenada (tipo -> id -> cmd) para generar el pack. */
    public synchronized Map<String, Map<String, Integer>> snapshot() {
        Map<String, Map<String, Integer>> out = new TreeMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : map.entrySet()) {
            out.put(e.getKey(), new TreeMap<>(e.getValue()));
        }
        return out;
    }

    public Material baseMaterial() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("pack.base-material", "PAPER"));
        return m == null || !m.isItem() ? Material.PAPER : m;
    }

    /** Ítem con el CMD del modelo, o null si ese modelo no está en el registro. */
    public ItemStack item(String type, String id) {
        Integer cmd = find(type, id);
        if (cmd == null) {
            return null;
        }
        return ItemBuilder.of(baseMaterial()).cmd(cmd).build();
    }
}
