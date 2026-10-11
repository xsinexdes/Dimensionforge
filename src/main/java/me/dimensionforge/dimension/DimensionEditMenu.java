package me.dimensionforge.dimension;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import me.dimensionforge.mob.MobListMenu;
import me.dimensionforge.ore.OreListMenu;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Menú de edición de una dimensión: cada ajuste es un ítem clicable. */
public final class DimensionEditMenu extends SettingsMenu {

    private final DimensionData d;

    public DimensionEditMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, DimensionData d) {
        super(plugin, viewer, parent, "<gold>Editar: " + d.name);
        this.d = d;
    }

    @Override
    protected void changed() {
        plugin.dimensions().apply(d);
        plugin.dimensions().save();
    }

    @Override
    protected void define() {
        boolean loaded = plugin.dimensions().isLoaded(d.name);
        info("Dimensión", Material.GRASS_BLOCK, "Nombre: " + d.name, "Entorno: " + d.environment.name(),
                "Estado: " + (loaded ? "cargada" : "descargada"));
        action(loaded ? "Descargar mundo" : "Cargar mundo", loaded ? Material.BARRIER : Material.LIME_CONCRETE, c -> {
            if (loaded) {
                if (!plugin.dimensions().unload(d)) {
                    plugin.messages().send(viewer, "dimension.unload-failed", "name", d.name);
                }
            } else if (plugin.dimensions().loadWorld(d) == null) {
                plugin.messages().send(viewer, "dimension.load-failed", "name", d.name);
            }
            refresh();
        }, "Guarda y evacúa a los jugadores al descargar");
        bool("Cargar al iniciar (autoload)", Material.REPEATER, () -> d.autoload, v -> d.autoload = v);
        action("Teletransportarme", Material.ENDER_PEARL, c -> {
            viewer.closeInventory();
            plugin.dimensions().teleport(viewer, d);
        });

        // spawn
        action("Spawn: usar mi posición", Material.RED_BED, c -> {
            if (!viewer.getWorld().getName().equals(d.name)) {
                plugin.messages().send(viewer, "dimension.must-be-inside", "name", d.name);
                return;
            }
            Location l = viewer.getLocation();
            d.hasSpawn = true;
            d.spawnX = l.getX();
            d.spawnY = l.getY();
            d.spawnZ = l.getZ();
            d.spawnYaw = l.getYaw();
            d.spawnPitch = l.getPitch();
            changed();
            refresh();
        }, d.hasSpawn ? String.format("Actual: %.1f %.1f %.1f", d.spawnX, d.spawnY, d.spawnZ) : "Sin spawn propio");

        // borde
        bool("WorldBorder activado", Material.IRON_BARS, () -> d.borderEnabled, v -> d.borderEnabled = v);
        dbl("WorldBorder tamaño", Material.SPYGLASS, () -> d.borderSize, v -> d.borderSize = v, 100, 1, 59999968);
        action("WorldBorder centro: mi posición", Material.COMPASS, c -> {
            d.borderX = viewer.getLocation().getX();
            d.borderZ = viewer.getLocation().getZ();
            changed();
            refresh();
        }, String.format("Centro: %.0f, %.0f", d.borderX, d.borderZ));

        // tiempo y clima
        cycle("Hora", Material.CLOCK, () -> d.timeMode, v -> d.timeMode = v, DimensionData.TimeMode.values(),
                "CYCLE = ciclo día/noche, FIXED = hora fija");
        intVal("Hora fija (ticks)", Material.CLOCK, () -> d.fixedTime, v -> d.fixedTime = v, 1000, 0, 24000,
                "0 amanecer · 6000 mediodía · 13000 noche");
        cycle("Clima", Material.WATER_BUCKET, () -> d.weather, v -> d.weather = v, DimensionData.WeatherMode.values(),
                "CLEAR = despejado fijo");

        // reglas
        cycle("Dificultad", Material.IRON_SWORD, () -> d.difficulty, v -> d.difficulty = v, Difficulty.values());
        bool("PvP", Material.DIAMOND_SWORD, () -> d.pvp, v -> d.pvp = v);
        bool("Spawn de monstruos", Material.ZOMBIE_HEAD, () -> d.spawnMonsters, v -> d.spawnMonsters = v);
        bool("Spawn de animales", Material.PORKCHOP, () -> d.spawnAnimals, v -> d.spawnAnimals = v);
        bool("keepInventory", Material.CHEST, () -> d.keepInventory, v -> d.keepInventory = v);
        bool("mobGriefing", Material.CREEPER_HEAD, () -> d.mobGriefing, v -> d.mobGriefing = v);
        bool("doFireTick", Material.FLINT_AND_STEEL, () -> d.doFireTick, v -> d.doFireTick = v);
        bool("doDaylightCycle", Material.SUNFLOWER, () -> d.doDaylightCycle, v -> d.doDaylightCycle = v,
                "Ignorado si la hora es FIXED");
        bool("doWeatherCycle", Material.LIGHTNING_ROD, () -> d.doWeatherCycle, v -> d.doWeatherCycle = v,
                "Ignorado si el clima es CLEAR");
        bool("doImmediateRespawn", Material.TOTEM_OF_UNDYING, () -> d.doImmediateRespawn, v -> d.doImmediateRespawn = v);
        intVal("randomTickSpeed", Material.WHEAT, () -> d.randomTickSpeed, v -> d.randomTickSpeed = v, 1, 0, 4096);

        // entrada y salida
        cycle("Modo de juego al entrar", Material.GOLDEN_APPLE, () -> d.defaultGameMode, v -> d.defaultGameMode = v,
                DimensionData.ModeOpt.values(), "NONE = no cambiar");
        str("Mensaje al entrar", Material.OAK_SIGN, () -> d.enterMessage, v -> d.enterMessage = v, "MiniMessage");
        str("Mensaje al salir", Material.OAK_SIGN, () -> d.exitMessage, v -> d.exitMessage = v, "MiniMessage");
        str("Título al entrar", Material.NAME_TAG, () -> d.enterTitle, v -> d.enterTitle = v, "Formato: Título|Subtítulo");
        str("Título al salir", Material.NAME_TAG, () -> d.exitTitle, v -> d.exitTitle = v, "Formato: Título|Subtítulo");
        str("Permiso para entrar", Material.TRIPWIRE_HOOK, () -> d.permission, v -> d.permission = v,
                "Vacío = sin restricción");
        bool("Habilidades de mobs activas", Material.BLAZE_POWDER, () -> d.skillsEnabled, v -> d.skillsEnabled = v,
                "Interruptor para desactivar las habilidades aquí");
        cycle("Generador (próxima carga)", Material.BEDROCK, () -> d.generator, v -> d.generator = v,
                DimensionData.Generator.values(), "Se aplica cuando el mundo se carga de nuevo");

        // accesos
        action("Ores custom de esta dimensión", Material.DIAMOND_ORE,
                c -> new OreListMenu(plugin, viewer, this, d.name).open());
        action("Mobs custom de esta dimensión", Material.ZOMBIE_SPAWN_EGG,
                c -> new MobListMenu(plugin, viewer, this, d.name).open());
        action("Borrar dimensión", Material.TNT, c -> MainMenu.confirmDelete(plugin, viewer, d, this),
                "Doble confirmación para borrar la carpeta");
    }
}
