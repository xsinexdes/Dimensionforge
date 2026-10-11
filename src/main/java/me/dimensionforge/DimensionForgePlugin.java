package me.dimensionforge;

import me.dimensionforge.cmd.DimensionCommand;
import me.dimensionforge.config.ConfigManager;
import me.dimensionforge.config.Messages;
import me.dimensionforge.dimension.DimensionListener;
import me.dimensionforge.dimension.DimensionManager;
import me.dimensionforge.gui.MenuListener;
import me.dimensionforge.gui.TextInput;
import me.dimensionforge.mob.MobListener;
import me.dimensionforge.mob.MobManager;
import me.dimensionforge.model.ModelBridge;
import me.dimensionforge.ore.OreListener;
import me.dimensionforge.ore.OreManager;
import me.dimensionforge.ore.OreMining;
import me.dimensionforge.pack.PackListener;
import me.dimensionforge.pack.PackManager;
import me.dimensionforge.portal.Portal;
import me.dimensionforge.portal.PortalListener;
import me.dimensionforge.portal.PortalManager;
import me.dimensionforge.portal.PortalWand;
import me.dimensionforge.skill.SkillListener;
import me.dimensionforge.skill.SkillManager;
import me.dimensionforge.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

/** Plugin principal: crea los gestores, registra eventos y tareas centrales. */
public final class DimensionForgePlugin extends JavaPlugin {

    private ConfigManager configs;
    private Messages messages;
    private TextInput textInput;
    private DimensionManager dimensions;
    private ModelBridge models;
    private PackManager packs;
    private OreManager ores;
    private OreMining oreMining;
    private PortalManager portals;
    private PortalWand wand;
    private MobManager mobs;
    private SkillManager skills;
    private PackListener packListener;

    @Override
    public void onEnable() {
        Keys.init(this);
        configs = new ConfigManager(this);
        configs.load();
        messages = new Messages(this);
        messages.load();
        textInput = new TextInput(this);

        // 1) Se crean todos los gestores antes de cargar nada (la carga de mundos llama a onWorldLoaded).
        dimensions = new DimensionManager(this);
        models = ModelBridge.create(this);
        packs = new PackManager(this);
        ores = new OreManager(this);
        oreMining = new OreMining(this, ores);
        portals = new PortalManager(this);
        wand = new PortalWand(this);
        mobs = new MobManager(this);
        skills = new SkillManager(this);
        packListener = new PackListener(this);

        // 2) Carga de datos
        dimensions.load();
        ores.load();
        portals.load();
        packs.start();
        skills.load();
        dimensions.loadAutoload();
        mobs.load();
        skills.start();
        mobs.start();
        models.onModelsReloaded(() -> mobs.reapplyModels());
        models.onModelsReloaded(() -> ores.refreshAllDisplays());

        // 3) Eventos
        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(textInput, this);
        pm.registerEvents(new DimensionListener(this), this);
        pm.registerEvents(new PortalListener(this), this);
        pm.registerEvents(new OreListener(this, ores, oreMining), this);
        pm.registerEvents(new MobListener(this), this);
        pm.registerEvents(new SkillListener(this), this);
        pm.registerEvents(packListener, this);

        // 4) Tareas centrales
        Bukkit.getScheduler().runTaskTimer(this, ores::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, oreMining::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, portals::tickFill, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, portals::tickParticles, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, wand::tickParticles, 10L, 10L);

        // 5) Comando
        PluginCommand cmd = getCommand("dimension");
        if (cmd != null) {
            DimensionCommand handler = new DimensionCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getLogger().info("DimensionForge activado. Modelos: " + models.provider() + ", habilidades: " + skills.count());
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (skills != null) {
            skills.shutdown();
        }
        if (mobs != null) {
            mobs.shutdown();
        }
        if (oreMining != null) {
            oreMining.endAll();
        }
        if (ores != null) {
            ores.shutdown();
        }
        if (portals != null) {
            portals.shutdown();
        }
        if (packs != null) {
            packs.shutdown();
        }
        if (models != null) {
            models.shutdown();
        }
        if (dimensions != null) {
            dimensions.shutdown();
        }
    }

    /** Recarga configuración, mensajes y todos los datos sin reiniciar. */
    public void reloadAll() {
        configs.load();
        messages.load();
        dimensions.load();
        ores.load();
        portals.load();
        packs.cmds().load();
        skills.shutdown();
        skills.load();
        skills.start();
        mobs.load();
        packs.build(null, null);
    }

    // ---------------------------------------------------------------- ganchos desde DimensionManager

    public void onWorldLoaded(World w) {
        ores.onWorldLoaded(w);
    }

    public void onWorldUnloading(World w) {
        ores.onWorldUnload(w);
        mobs.removeInWorld(w);
    }

    /** Se llama al quitar una dimensión de la gestión: limpia lo que dependía de ella. */
    public void onDimensionRemoved(String name) {
        ores.removeByDimension(name);
        mobs.removeByDimension(name);
        for (Portal p : new ArrayList<>(portals.all())) {
            if (p.world.equals(name) || p.destination.equals(name)) {
                try {
                    portals.remove(p);
                } catch (RuntimeException ex) {
                    getLogger().warning("No se pudo limpiar el portal " + p.id + ": " + ex);
                }
            }
        }
    }

    // ---------------------------------------------------------------- accesores

    public ConfigManager configs() {
        return configs;
    }

    public Messages messages() {
        return messages;
    }

    public TextInput textInput() {
        return textInput;
    }

    public DimensionManager dimensions() {
        return dimensions;
    }

    public ModelBridge models() {
        return models;
    }

    public PackManager packs() {
        return packs;
    }

    public PackListener packListener() {
        return packListener;
    }

    public OreManager ores() {
        return ores;
    }

    public OreMining oreMining() {
        return oreMining;
    }

    public PortalManager portals() {
        return portals;
    }

    public PortalWand wand() {
        return wand;
    }

    public MobManager mobs() {
        return mobs;
    }

    public SkillManager skills() {
        return skills;
    }
}
