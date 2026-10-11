package me.dimensionforge.dimension;

import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** Datos persistentes de una dimensión administrada (dimensions.yml). */
public final class DimensionData {

    public enum DimType { NORMAL, FLAT, AMPLIFIED, LARGE_BIOMES, VOID }

    public enum Generator { VANILLA, VOID }

    public enum TimeMode { CYCLE, FIXED }

    public enum WeatherMode { NORMAL, CLEAR }

    public enum ModeOpt { NONE, SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR }

    public final String name;
    public World.Environment environment = World.Environment.NORMAL;
    public DimType type = DimType.NORMAL;
    public Generator generator = Generator.VANILLA;
    public String seedText = "";
    public long seed = 0L;
    public boolean hasSeed = false;
    public boolean structures = true;
    public boolean autoload = true;

    // spawn
    public boolean hasSpawn = false;
    public double spawnX, spawnY = 64, spawnZ;
    public float spawnYaw, spawnPitch;

    // borde
    public boolean borderEnabled = false;
    public double borderSize = 1000;
    public double borderX, borderZ;

    // tiempo y clima
    public TimeMode timeMode = TimeMode.CYCLE;
    public int fixedTime = 6000;
    public WeatherMode weather = WeatherMode.NORMAL;

    // reglas
    public Difficulty difficulty = Difficulty.NORMAL;
    public boolean pvp = true;
    public boolean spawnMonsters = true;
    public boolean spawnAnimals = true;
    public boolean keepInventory = false;
    public boolean mobGriefing = true;
    public boolean doFireTick = true;
    public boolean doDaylightCycle = true;
    public boolean doWeatherCycle = true;
    public boolean doImmediateRespawn = false;
    public int randomTickSpeed = 3;

    // entrada/salida
    public ModeOpt defaultGameMode = ModeOpt.NONE;
    public String enterMessage = "";
    public String exitMessage = "";
    public String enterTitle = "";
    public String exitTitle = "";
    public String permission = "";

    // habilidades
    public boolean skillsEnabled = true;

    public DimensionData(String name) {
        this.name = name;
    }

    public boolean voidTerrain() {
        return type == DimType.VOID || generator == Generator.VOID;
    }

    public void save(ConfigurationSection s) {
        s.set("environment", environment.name());
        s.set("type", type.name());
        s.set("generator", generator.name());
        s.set("seed-text", seedText);
        s.set("seed", hasSeed ? seed : null);
        s.set("structures", structures);
        s.set("autoload", autoload);
        s.set("spawn.enabled", hasSpawn);
        s.set("spawn.x", spawnX);
        s.set("spawn.y", spawnY);
        s.set("spawn.z", spawnZ);
        s.set("spawn.yaw", (double) spawnYaw);
        s.set("spawn.pitch", (double) spawnPitch);
        s.set("border.enabled", borderEnabled);
        s.set("border.size", borderSize);
        s.set("border.x", borderX);
        s.set("border.z", borderZ);
        s.set("time.mode", timeMode.name());
        s.set("time.fixed", fixedTime);
        s.set("weather", weather.name());
        s.set("rules.difficulty", difficulty.name());
        s.set("rules.pvp", pvp);
        s.set("rules.spawn-monsters", spawnMonsters);
        s.set("rules.spawn-animals", spawnAnimals);
        s.set("rules.keepInventory", keepInventory);
        s.set("rules.mobGriefing", mobGriefing);
        s.set("rules.doFireTick", doFireTick);
        s.set("rules.doDaylightCycle", doDaylightCycle);
        s.set("rules.doWeatherCycle", doWeatherCycle);
        s.set("rules.doImmediateRespawn", doImmediateRespawn);
        s.set("rules.randomTickSpeed", randomTickSpeed);
        s.set("enter.game-mode", defaultGameMode.name());
        s.set("enter.message", enterMessage);
        s.set("enter.title", enterTitle);
        s.set("exit.message", exitMessage);
        s.set("exit.title", exitTitle);
        s.set("permission", permission);
        s.set("skills-enabled", skillsEnabled);
    }

    public static DimensionData load(String name, ConfigurationSection s) {
        DimensionData d = new DimensionData(name);
        d.environment = enumOf(World.Environment.class, s.getString("environment"), World.Environment.NORMAL);
        d.type = enumOf(DimType.class, s.getString("type"), DimType.NORMAL);
        d.generator = enumOf(Generator.class, s.getString("generator"), Generator.VANILLA);
        d.seedText = s.getString("seed-text", "");
        d.hasSeed = s.contains("seed");
        d.seed = s.getLong("seed", 0L);
        d.structures = s.getBoolean("structures", true);
        d.autoload = s.getBoolean("autoload", true);
        d.hasSpawn = s.getBoolean("spawn.enabled", false);
        d.spawnX = s.getDouble("spawn.x");
        d.spawnY = s.getDouble("spawn.y", 64);
        d.spawnZ = s.getDouble("spawn.z");
        d.spawnYaw = (float) s.getDouble("spawn.yaw");
        d.spawnPitch = (float) s.getDouble("spawn.pitch");
        d.borderEnabled = s.getBoolean("border.enabled", false);
        d.borderSize = s.getDouble("border.size", 1000);
        d.borderX = s.getDouble("border.x");
        d.borderZ = s.getDouble("border.z");
        d.timeMode = enumOf(TimeMode.class, s.getString("time.mode"), TimeMode.CYCLE);
        d.fixedTime = s.getInt("time.fixed", 6000);
        d.weather = enumOf(WeatherMode.class, s.getString("weather"), WeatherMode.NORMAL);
        d.difficulty = enumOf(Difficulty.class, s.getString("rules.difficulty"), Difficulty.NORMAL);
        d.pvp = s.getBoolean("rules.pvp", true);
        d.spawnMonsters = s.getBoolean("rules.spawn-monsters", true);
        d.spawnAnimals = s.getBoolean("rules.spawn-animals", true);
        d.keepInventory = s.getBoolean("rules.keepInventory", false);
        d.mobGriefing = s.getBoolean("rules.mobGriefing", true);
        d.doFireTick = s.getBoolean("rules.doFireTick", true);
        d.doDaylightCycle = s.getBoolean("rules.doDaylightCycle", true);
        d.doWeatherCycle = s.getBoolean("rules.doWeatherCycle", true);
        d.doImmediateRespawn = s.getBoolean("rules.doImmediateRespawn", false);
        d.randomTickSpeed = s.getInt("rules.randomTickSpeed", 3);
        d.defaultGameMode = enumOf(ModeOpt.class, s.getString("enter.game-mode"), ModeOpt.NONE);
        d.enterMessage = s.getString("enter.message", "");
        d.enterTitle = s.getString("enter.title", "");
        d.exitMessage = s.getString("exit.message", "");
        d.exitTitle = s.getString("exit.title", "");
        d.permission = s.getString("permission", "");
        d.skillsEnabled = s.getBoolean("skills-enabled", true);
        return d;
    }

    public static <E extends Enum<E>> E enumOf(Class<E> type, String name, E def) {
        if (name == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
