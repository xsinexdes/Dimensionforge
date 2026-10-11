package me.dimensionforge.config;

import me.dimensionforge.DimensionForgePlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** Acceso tipado a config.yml. */
public final class ConfigManager {

    private final DimensionForgePlugin plugin;

    public ConfigManager(DimensionForgePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(true);
    }

    public FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public String inputMode() {
        return cfg().getString("gui.input-mode", "CHAT").toUpperCase();
    }

    public int inputTimeoutSeconds() {
        return cfg().getInt("gui.input-timeout-seconds", 60);
    }

    public String safeWorld() {
        return cfg().getString("dimensions.safe-world", "");
    }

    public List<String> importBlacklist() {
        return cfg().getStringList("dimensions.import-blacklist");
    }

    public boolean worldGuardEnabled() {
        return cfg().getBoolean("global.respect-worldguard", true);
    }

    public boolean noBlockDamage() {
        return cfg().getBoolean("global.skills-no-block-damage", true);
    }

    public boolean skipCreativeSpectator() {
        return cfg().getBoolean("global.skills-skip-creative-spectator", true);
    }
}
