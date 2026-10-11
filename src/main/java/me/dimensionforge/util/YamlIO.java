package me.dimensionforge.util;

import me.dimensionforge.DimensionForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/** Carga y guardado seguro de YAML (el guardado pesado va en async, la serialización en el hilo principal). */
public final class YamlIO {

    private static final AtomicInteger PENDING = new AtomicInteger();

    private YamlIO() {
    }

    public static YamlConfiguration load(DimensionForgePlugin plugin, File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!file.exists()) {
            return yaml;
        }
        try {
            yaml.load(file);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo leer " + file.getName() + ". Se hace copia .broken", ex);
            try {
                Files.copy(file.toPath(), new File(file.getParentFile(), file.getName() + ".broken").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // sin copia de respaldo
            }
            return new YamlConfiguration();
        }
        return yaml;
    }

    /** Serializa en el hilo actual y escribe a disco en un hilo aparte. */
    public static void saveAsync(DimensionForgePlugin plugin, File file, YamlConfiguration yaml) {
        final String data = yaml.saveToString();
        if (!plugin.isEnabled()) {
            write(plugin, file, data);
            return;
        }
        PENDING.incrementAndGet();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                write(plugin, file, data);
            } finally {
                PENDING.decrementAndGet();
            }
        });
    }

    /** Guardado síncrono (apagado del servidor). */
    public static void saveSync(DimensionForgePlugin plugin, File file, YamlConfiguration yaml) {
        write(plugin, file, yaml.saveToString());
    }

    private static synchronized void write(DimensionForgePlugin plugin, File file, String data) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar " + file.getName(), ex);
        }
    }
}
