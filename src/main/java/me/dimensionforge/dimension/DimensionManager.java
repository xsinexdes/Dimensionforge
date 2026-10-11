package me.dimensionforge.dimension;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.YamlIO;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Crea, importa, carga, descarga y configura las dimensiones (mundos) administradas. */
public final class DimensionManager {

    public static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

    private final DimensionForgePlugin plugin;
    private final File file;
    private final Map<String, DimensionData> dims = new LinkedHashMap<>();

    public DimensionManager(DimensionForgePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dimensions.yml");
    }

    // ---------------------------------------------------------------- persistencia

    public void load() {
        dims.clear();
        YamlConfiguration yaml = YamlIO.load(plugin, file);
        ConfigurationSection root = yaml.getConfigurationSection("dimensions");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s != null) {
                    dims.put(key, DimensionData.load(key, s));
                }
            }
        }
    }

    /** Carga los mundos con autoload y aplica sus ajustes. */
    public void loadAutoload() {
        for (DimensionData d : new ArrayList<>(dims.values())) {
            if (d.autoload && Bukkit.getWorld(d.name) == null) {
                World w = loadWorld(d);
                if (w == null) {
                    plugin.getLogger().warning("No se pudo cargar la dimensión '" + d.name + "' (¿carpeta borrada o corrupta?)");
                }
            } else if (Bukkit.getWorld(d.name) != null) {
                apply(d);
            }
        }
    }

    public void save() {
        YamlIO.saveAsync(plugin, file, toYaml());
    }

    public void saveSync() {
        YamlIO.saveSync(plugin, file, toYaml());
    }

    private YamlConfiguration toYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (DimensionData d : dims.values()) {
            d.save(yaml.createSection("dimensions." + d.name));
        }
        return yaml;
    }

    // ---------------------------------------------------------------- consulta

    public Collection<DimensionData> all() {
        return Collections.unmodifiableCollection(dims.values());
    }

    public DimensionData get(String name) {
        return name == null ? null : dims.get(name.toLowerCase());
    }

    public World world(String name) {
        return Bukkit.getWorld(name);
    }

    public boolean isLoaded(String name) {
        return Bukkit.getWorld(name) != null;
    }

    public boolean isManaged(World w) {
        return w != null && dims.containsKey(w.getName());
    }

    public DimensionData of(World w) {
        return w == null ? null : dims.get(w.getName());
    }

    public List<String> names() {
        return new ArrayList<>(dims.keySet());
    }

    public String validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            return "error.dimension-name-invalid";
        }
        if (dims.containsKey(name) || Bukkit.getWorld(name) != null) {
            return "error.dimension-name-exists";
        }
        if (new File(Bukkit.getWorldContainer(), name).exists()) {
            return "error.dimension-folder-exists";
        }
        return null;
    }

    // ---------------------------------------------------------------- creación

    /** Registra y crea la dimensión. La creación real ocurre 2 ticks después para que el jugador vea el progreso. */
    public void createAsync(DimensionData d, CommandSender who, Runnable done) {
        plugin.messages().send(who, "dimension.creating", "name", d.name);
        dims.put(d.name, d);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World w = null;
            try {
                if (!d.hasSeed) {
                    if (d.seedText != null && !d.seedText.isBlank()) {
                        try {
                            d.seed = Long.parseLong(d.seedText.trim());
                        } catch (NumberFormatException ex) {
                            d.seed = d.seedText.hashCode();
                        }
                    } else {
                        d.seed = new Random().nextLong();
                    }
                    d.hasSeed = true;
                }
                w = loadWorld(d);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Fallo creando dimensión " + d.name, ex);
            }
            if (w == null) {
                dims.remove(d.name);
                plugin.messages().send(who, "dimension.create-failed", "name", d.name);
                return;
            }
            plugin.messages().send(who, "dimension.applying", "name", d.name);
            if (d.voidTerrain() && plugin.getConfig().getBoolean("dimensions.void-platform", true)) {
                placeVoidPlatform(w);
            }
            if (!d.hasSpawn) {
                Location sp = w.getSpawnLocation();
                d.spawnX = sp.getX() + 0.5;
                d.spawnY = sp.getY();
                d.spawnZ = sp.getZ() + 0.5;
            }
            apply(d);
            save();
            plugin.messages().send(who, "dimension.created", "name", d.name);
            if (done != null) {
                done.run();
            }
        }, 2L);
    }

    private void placeVoidPlatform(World w) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (w.getBlockAt(x, 63, z).getType().isAir()) {
                    w.getBlockAt(x, 63, z).setType(Material.STONE);
                }
            }
        }
    }

    /** Carga (o crea si no existe la carpeta) el mundo de la dimensión. Devuelve null si falla. */
    public World loadWorld(DimensionData d) {
        World existing = Bukkit.getWorld(d.name);
        if (existing != null) {
            apply(d);
            return existing;
        }
        try {
            WorldCreator wc = new WorldCreator(d.name);
            wc.environment(d.environment);
            switch (d.type) {
                case FLAT -> wc.type(WorldType.FLAT);
                case AMPLIFIED -> wc.type(WorldType.AMPLIFIED);
                case LARGE_BIOMES -> wc.type(WorldType.LARGE_BIOMES);
                default -> wc.type(WorldType.NORMAL);
            }
            wc.generateStructures(d.structures);
            if (d.hasSeed) {
                wc.seed(d.seed);
            }
            if (d.voidTerrain()) {
                wc.generator(new VoidChunkGenerator());
            }
            World w = wc.createWorld();
            if (w != null) {
                apply(d);
                plugin.onWorldLoaded(w);
            }
            return w;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo cargar el mundo " + d.name, ex);
            return null;
        }
    }

    // ---------------------------------------------------------------- importación

    /** Carpetas con level.dat que no están cargadas ni en la lista negra. */
    public List<String> scanImportable() {
        List<String> out = new ArrayList<>();
        File[] files = Bukkit.getWorldContainer().listFiles();
        if (files == null) {
            return out;
        }
        List<String> black = plugin.configs().importBlacklist();
        for (File f : files) {
            if (!f.isDirectory() || !new File(f, "level.dat").exists()) {
                continue;
            }
            String n = f.getName();
            if (Bukkit.getWorld(n) != null || dims.containsKey(n)) {
                continue;
            }
            boolean blocked = false;
            for (String b : black) {
                if (b.equalsIgnoreCase(n)) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                out.add(n);
            }
        }
        Collections.sort(out);
        return out;
    }

    public World.Environment detectEnvironment(String folder) {
        File dir = new File(Bukkit.getWorldContainer(), folder);
        if (new File(dir, "DIM-1").exists()) {
            return World.Environment.NETHER;
        }
        if (new File(dir, "DIM1").exists()) {
            return World.Environment.THE_END;
        }
        return World.Environment.NORMAL;
    }

    /** Importa una carpeta de mundo existente como dimensión administrada. */
    public DimensionData importWorld(String folder, World.Environment env, CommandSender who) {
        DimensionData d = new DimensionData(folder);
        d.environment = env;
        d.autoload = true;
        d.structures = true;
        dims.put(folder, d);
        World w = loadWorld(d);
        if (w == null) {
            dims.remove(folder);
            plugin.messages().send(who, "dimension.import-failed", "name", folder);
            return null;
        }
        // Tomar los valores reales del mundo como punto de partida
        Location sp = w.getSpawnLocation();
        d.hasSpawn = true;
        d.spawnX = sp.getX() + 0.5;
        d.spawnY = sp.getY();
        d.spawnZ = sp.getZ() + 0.5;
        d.difficulty = w.getDifficulty();
        d.pvp = w.getPVP();
        d.hasSeed = true;
        d.seed = w.getSeed();
        d.keepInventory = bool(w, GameRule.KEEP_INVENTORY, false);
        d.mobGriefing = bool(w, GameRule.MOB_GRIEFING, true);
        d.doFireTick = bool(w, GameRule.DO_FIRE_TICK, true);
        d.doDaylightCycle = bool(w, GameRule.DO_DAYLIGHT_CYCLE, true);
        d.doWeatherCycle = bool(w, GameRule.DO_WEATHER_CYCLE, true);
        d.doImmediateRespawn = bool(w, GameRule.DO_IMMEDIATE_RESPAWN, false);
        Integer rts = w.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
        d.randomTickSpeed = rts == null ? 3 : rts;
        save();
        plugin.messages().send(who, "dimension.imported", "name", folder);
        return d;
    }

    private boolean bool(World w, GameRule<Boolean> rule, boolean def) {
        Boolean v = w.getGameRuleValue(rule);
        return v == null ? def : v;
    }

    // ---------------------------------------------------------------- aplicar ajustes

    /** Aplica todos los ajustes de la dimensión al mundo cargado, sin reiniciar. */
    public void apply(DimensionData d) {
        World w = Bukkit.getWorld(d.name);
        if (w == null) {
            return;
        }
        try {
            w.setDifficulty(d.difficulty);
            w.setPVP(d.pvp);
            w.setSpawnFlags(d.spawnMonsters, d.spawnAnimals);
            w.setGameRule(GameRule.KEEP_INVENTORY, d.keepInventory);
            w.setGameRule(GameRule.MOB_GRIEFING, d.mobGriefing);
            w.setGameRule(GameRule.DO_FIRE_TICK, d.doFireTick);
            w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, d.doImmediateRespawn);
            w.setGameRule(GameRule.RANDOM_TICK_SPEED, d.randomTickSpeed);

            boolean daylight = d.timeMode == DimensionData.TimeMode.CYCLE && d.doDaylightCycle;
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, daylight);
            if (d.timeMode == DimensionData.TimeMode.FIXED) {
                w.setTime(d.fixedTime);
            }
            boolean weatherCycle = d.weather == DimensionData.WeatherMode.NORMAL && d.doWeatherCycle;
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, weatherCycle);
            if (d.weather == DimensionData.WeatherMode.CLEAR) {
                w.setStorm(false);
                w.setThundering(false);
            }
            WorldBorder wb = w.getWorldBorder();
            if (d.borderEnabled) {
                wb.setCenter(d.borderX, d.borderZ);
                wb.setSize(Math.max(1.0, d.borderSize));
            } else {
                wb.setCenter(0, 0);
                wb.setSize(59999968);
            }
            if (d.hasSpawn) {
                w.setSpawnLocation((int) Math.floor(d.spawnX), (int) Math.floor(d.spawnY), (int) Math.floor(d.spawnZ));
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Error aplicando ajustes a " + d.name, ex);
        }
    }

    public Location spawnOf(DimensionData d) {
        World w = Bukkit.getWorld(d.name);
        if (w == null) {
            return null;
        }
        if (d.hasSpawn) {
            return new Location(w, d.spawnX, d.spawnY, d.spawnZ, d.spawnYaw, d.spawnPitch);
        }
        return w.getSpawnLocation().add(0.5, 0, 0.5);
    }

    // ---------------------------------------------------------------- teletransporte

    public World ensureLoaded(DimensionData d) {
        World w = Bukkit.getWorld(d.name);
        if (w == null) {
            w = loadWorld(d);
        }
        return w;
    }

    public void teleport(Player p, DimensionData d) {
        World w = ensureLoaded(d);
        if (w == null) {
            plugin.messages().send(p, "dimension.not-loaded", "name", d.name);
            return;
        }
        Location loc = spawnOf(d);
        p.teleportAsync(loc).thenAccept(ok -> {
            if (ok) {
                plugin.messages().send(p, "dimension.teleported", "name", d.name);
            }
        });
    }

    public World safeWorld() {
        String configured = plugin.configs().safeWorld();
        if (configured != null && !configured.isBlank()) {
            World w = Bukkit.getWorld(configured);
            if (w != null) {
                return w;
            }
        }
        return Bukkit.getWorlds().get(0);
    }

    // ---------------------------------------------------------------- descarga y borrado

    /** Evacúa jugadores y descarga el mundo guardándolo. */
    public boolean unload(DimensionData d) {
        World w = Bukkit.getWorld(d.name);
        if (w == null) {
            return true;
        }
        if (w.equals(Bukkit.getWorlds().get(0))) {
            return false;
        }
        evacuate(w);
        plugin.onWorldUnloading(w);
        boolean ok = Bukkit.unloadWorld(w, true);
        if (!ok) {
            plugin.getLogger().warning("Bukkit se negó a descargar " + d.name);
        }
        return ok;
    }

    private void evacuate(World w) {
        World safe = safeWorld();
        if (safe.equals(w)) {
            return;
        }
        Location target = safe.getSpawnLocation().add(0.5, 0, 0.5);
        for (Player p : new ArrayList<>(w.getPlayers())) {
            p.teleport(target);
            plugin.messages().send(p, "dimension.evacuated");
        }
    }

    /** Quita la dimensión de la gestión, la descarga y, solo si se pide, borra la carpeta. */
    public boolean delete(DimensionData d, boolean deleteFolder) {
        if (!unload(d)) {
            return false;
        }
        dims.remove(d.name);
        plugin.onDimensionRemoved(d.name);
        save();
        if (deleteFolder) {
            final File dir = new File(Bukkit.getWorldContainer(), d.name);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteRecursive(dir.toPath()));
        }
        return true;
    }

    private void deleteRecursive(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(f);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo borrar la carpeta " + root, ex);
        }
    }

    /** Guarda y descarga todo al apagar (los mundos los guarda el servidor). */
    public void shutdown() {
        saveSync();
    }
}
