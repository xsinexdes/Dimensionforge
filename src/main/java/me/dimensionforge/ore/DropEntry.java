package me.dimensionforge.ore;

import me.dimensionforge.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Un ítem que puede soltar una mena o un mob. */
public final class DropEntry {

    public String material = "DIAMOND";
    public int min = 1;
    public int max = 1;
    /** 0 = sin CustomModelData. */
    public int cmd = 0;
    public String name = "";
    public List<String> lore = new ArrayList<>();
    /** Probabilidad en porcentaje 0-100. */
    public double chance = 100.0;

    public DropEntry() {
    }

    public static DropEntry fromItem(ItemStack item) {
        DropEntry d = new DropEntry();
        d.material = item.getType().name();
        d.min = item.getAmount();
        d.max = item.getAmount();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasCustomModelData()) {
                d.cmd = meta.getCustomModelData();
            }
            if (meta.hasDisplayName() && meta.displayName() != null) {
                d.name = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(meta.displayName());
            }
            if (meta.hasLore() && meta.lore() != null) {
                for (net.kyori.adventure.text.Component c : meta.lore()) {
                    d.lore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(c));
                }
            }
        }
        return d;
    }

    public Material mat() {
        Material m = Material.matchMaterial(material == null ? "" : material);
        return m == null || m.isAir() || !m.isItem() ? Material.STONE : m;
    }

    /** Crea el ítem para una cantidad concreta. */
    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(mat(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isBlank()) {
                meta.displayName(Text.mm(name));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.lore(Text.mm(lore));
            }
            if (cmd > 0) {
                meta.setCustomModelData(cmd);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean roll(ThreadLocalRandom rnd) {
        return chance >= 100.0 || rnd.nextDouble() * 100.0 < chance;
    }

    public int rollAmount(ThreadLocalRandom rnd) {
        int lo = Math.max(0, Math.min(min, max));
        int hi = Math.max(lo, Math.max(min, max));
        return lo == hi ? lo : lo + rnd.nextInt(hi - lo + 1);
    }

    public void save(ConfigurationSection s) {
        s.set("material", material);
        s.set("min", min);
        s.set("max", max);
        s.set("cmd", cmd);
        s.set("name", name);
        s.set("lore", lore);
        s.set("chance", chance);
    }

    public static DropEntry load(ConfigurationSection s) {
        DropEntry d = new DropEntry();
        d.material = s.getString("material", "DIAMOND");
        d.min = s.getInt("min", 1);
        d.max = s.getInt("max", d.min);
        d.cmd = s.getInt("cmd", 0);
        d.name = s.getString("name", "");
        d.lore = new ArrayList<>(s.getStringList("lore"));
        d.chance = s.getDouble("chance", 100.0);
        return d;
    }

    public static List<DropEntry> loadList(ConfigurationSection parent, String key) {
        List<DropEntry> out = new ArrayList<>();
        ConfigurationSection sec = parent.getConfigurationSection(key);
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                ConfigurationSection e = sec.getConfigurationSection(k);
                if (e != null) {
                    out.add(load(e));
                }
            }
        }
        return out;
    }

    public static void saveList(ConfigurationSection parent, String key, List<DropEntry> list) {
        parent.set(key, null);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).save(parent.createSection(key + "." + i));
        }
    }
}
