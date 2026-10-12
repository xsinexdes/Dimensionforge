package me.dimensionforge.model;

import me.dimensionforge.DimensionForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/**
 * Puente con un plugin de modelos (BetterModel). Si no está instalado se usa SimpleModelBridge,
 * que no hace nada y permite que el resto del plugin funcione con mobs "simples".
 */
public interface ModelBridge {

    /** true si el plugin de modelos está presente y activo. */
    boolean available();

    String provider();

    /** Nombres de los modelos cargados, ordenados alfabéticamente. */
    List<String> modelNames();

    boolean hasModel(String name);

    /** Nombres de animación del modelo (vacío si no existe). */
    List<String> animations(String model);

    /** Duración en segundos de una animación, o -1 si no se conoce. */
    double animationLength(String model, String animation);

    /** Pone el modelo sobre la entidad (idempotente: no duplica si ya lo tiene). */
    boolean apply(LivingEntity entity, String model, double scale);

    /** Quita el modelo de la entidad, si lo tiene. */
    void remove(Entity entity, String model);

    boolean has(Entity entity, String model);

    /** Reproduce una animación. loop=true la repite hasta que se pare. */
    boolean animate(Entity entity, String model, String animation, boolean loop);

    void stopAnimation(Entity entity, String model, String animation);

    /** Reproduce una animación de muerte sobre un tracker temporal en la posición dada. */
    boolean playDeath(Location where, String model, String animation, double scale);

    /** Modelo estático (por ejemplo sobre un bloque). Cerrar el resultado lo elimina. */
    AutoCloseable spawnStatic(Location where, String model, float scale);

    /** Registra una acción que se ejecuta cuando el plugin de modelos termina de (re)cargar sus modelos. */
    void onModelsReloaded(Runnable action);

    /** Libera todo lo que haya creado el puente (trackers temporales, entidades auxiliares). */
    void shutdown();

    static ModelBridge create(DimensionForgePlugin plugin) {
        boolean enabled = plugin.getConfig().getBoolean("models.enabled", true);
        if (enabled && Bukkit.getPluginManager().isPluginEnabled("BetterModel")) {
            try {
                return new BetterModelBridge(plugin);
            } catch (Throwable t) {
                plugin.getLogger().warning("BetterModel está instalado pero su API no es compatible: " + t
                        + ". Se usará el modo simple (sin modelos).");
            }
        }
        return new SimpleModelBridge();
    }
}
