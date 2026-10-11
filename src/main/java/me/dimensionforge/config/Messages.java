package me.dimensionforge.config;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Mensajes en MiniMessage cargados desde messages.yml. */
public final class Messages {

    private final DimensionForgePlugin plugin;
    private YamlConfiguration yaml = new YamlConfiguration();
    private YamlConfiguration defaults = new YamlConfiguration();

    public Messages(DimensionForgePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("messages.yml inválido, se usan los mensajes por defecto: " + ex.getMessage());
            yaml = new YamlConfiguration();
        }
        defaults = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            defaults.load(reader);
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo leer messages.yml embebido: " + ex.getMessage());
        }
    }

    /** Texto crudo con los placeholders {clave} ya sustituidos (valores escapados). */
    public String raw(String key, Object... pairs) {
        String s = yaml.getString(key);
        if (s == null) {
            s = defaults.getString(key);
        }
        if (s == null) {
            return "<red>Mensaje faltante: " + key;
        }
        s = s.replace("{prefix}", prefixRaw());
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = s.replace("{" + pairs[i] + "}", Text.escape(String.valueOf(pairs[i + 1])));
        }
        return s;
    }

    public List<String> rawList(String key, Object... pairs) {
        List<String> list = yaml.getStringList(key);
        if (list.isEmpty()) {
            list = defaults.getStringList(key);
        }
        List<String> out = new ArrayList<>();
        for (String s : list) {
            String r = s.replace("{prefix}", prefixRaw());
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                r = r.replace("{" + pairs[i] + "}", Text.escape(String.valueOf(pairs[i + 1])));
            }
            out.add(r);
        }
        return out;
    }

    public Component get(String key, Object... pairs) {
        return Text.mm(raw(key, pairs));
    }

    public void send(CommandSender to, String key, Object... pairs) {
        to.sendMessage(get(key, pairs));
    }

    private String prefixRaw() {
        String p = yaml.getString("prefix");
        if (p == null) {
            p = defaults.getString("prefix", "");
        }
        return p;
    }
}
