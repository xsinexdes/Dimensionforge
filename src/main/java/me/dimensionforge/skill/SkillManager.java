package me.dimensionforge.skill;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.mob.MobState;
import me.dimensionforge.skill.model.BossPhase;
import me.dimensionforge.skill.model.SignatureSkills;
import me.dimensionforge.skill.model.SkillDef;
import me.dimensionforge.skill.model.SkillLibraryGenerator;
import me.dimensionforge.skill.model.Trigger;
import me.dimensionforge.util.YamlIO;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.dimensionforge.mob.MobDef;
import me.dimensionforge.skill.model.SkillBinding;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Biblioteca de habilidades (1000+ generadas + firmas + personalizadas) y acceso al motor. */
public final class SkillManager {

    /** Sube este número para forzar la regeneración de skills/library.yml tras cambiar el generador. */
    private static final int LIBRARY_VERSION = 1;

    private final DimensionForgePlugin plugin;
    private final File libraryFile;
    private final File customFile;
    private final Map<String, SkillDef> library = new LinkedHashMap<>();
    private SkillEngine engine;
    private WorldGuardHook worldGuard;

    public SkillManager(DimensionForgePlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "skills");
        dir.mkdirs();
        this.libraryFile = new File(dir, "library.yml");
        this.customFile = new File(dir, "custom.yml");
    }

    public SkillEngine engine() {
        return engine;
    }

    public WorldGuardHook worldGuard() {
        return worldGuard;
    }

    public void load() {
        library.clear();
        YamlConfiguration yaml = libraryFile.exists() ? YamlIO.load(plugin, libraryFile) : new YamlConfiguration();
        if (!libraryFile.exists() || yaml.getInt("version", 0) != LIBRARY_VERSION
                || yaml.getConfigurationSection("skills") == null) {
            regenerateFile();
            yaml = YamlIO.load(plugin, libraryFile);
        }
        readSkills(yaml, false);
        if (customFile.exists()) {
            readSkills(YamlIO.load(plugin, customFile), true);
        }
        plugin.getLogger().info("Biblioteca de habilidades: " + library.size() + " habilidades.");
    }

    private void readSkills(YamlConfiguration yaml, boolean custom) {
        ConfigurationSection root = yaml.getConfigurationSection("skills");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            SkillDef d = SkillDef.fromMap(id, s.getValues(false));
            if (custom) {
                d.signature = true;
            }
            library.put(id, d);
        }
    }

    /** Vuelve a generar library.yml desde el generador (las personalizadas en custom.yml no se tocan). */
    public int regenerateFile() {
        List<SkillDef> all = new ArrayList<>(SkillLibraryGenerator.generate());
        all.addAll(SignatureSkills.all());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", LIBRARY_VERSION);
        for (SkillDef d : all) {
            ConfigurationSection s = yaml.createSection("skills." + d.id);
            for (Map.Entry<String, Object> e : d.toMap().entrySet()) {
                s.set(e.getKey(), e.getValue());
            }
        }
        YamlIO.saveSync(plugin, libraryFile, yaml);
        return all.size();
    }

    public void regenerate() {
        regenerateFile();
        load();
    }

    public void start() {
        shutdown();
        worldGuard = new WorldGuardHook(plugin.getLogger());
        engine = new SkillEngine(plugin, this);
        engine.start();
    }

    public void shutdown() {
        if (engine != null) {
            engine.shutdown();
            engine = null;
        }
    }

    public SkillDef get(String id) {
        return library.get(id);
    }

    public Collection<SkillDef> all() {
        return library.values();
    }

    public int count() {
        return library.size();
    }

    // ---------------------------------------------------------------- puente desde los listeners de mobs

    public void fire(MobState st, Trigger trigger, LivingEntity other) {
        if (engine != null) {
            engine.fire(st, trigger, other);
        }
    }

    public void onHitByWeapon(MobState st, Player attacker, ItemStack weapon, boolean crit) {
        if (engine != null) {
            engine.onHitByWeapon(st, attacker, weapon, crit);
        }
    }

    public void onPhaseEntered(MobState st, BossPhase phase) {
        if (engine != null) {
            engine.onPhaseEntered(st, phase);
        }
    }

    /** Elimina las invocaciones de un mob (cuando se borra su definición o se descarta). */
    public void discardSummons(MobState st) {
        if (engine != null) {
            engine.removeSummonsOf(st.id);
        }
    }

    public void forgetMob(MobState st) {
        if (engine != null) {
            engine.forgetMob(st);
        }
    }

    /** Prueba una habilidad ya asignada: usa un mob cercano del tipo o crea uno temporal sin IA. */
    public boolean test(Player viewer, MobDef def, SkillBinding b) {
        SkillDef base = get(b.skillId);
        if (base == null || engine == null) {
            return false;
        }
        MobState st = null;
        double best = 48.0 * 48.0;
        for (MobState s : plugin.mobs().active()) {
            if (s.def == def && s.entity.getWorld().equals(viewer.getWorld())) {
                double dd = s.entity.getLocation().distanceSquared(viewer.getLocation());
                if (dd < best) {
                    best = dd;
                    st = s;
                }
            }
        }
        boolean temporary = false;
        if (st == null) {
            org.bukkit.Location l = viewer.getLocation().add(viewer.getLocation().getDirection().setY(0).normalize().multiply(7));
            LivingEntity e = plugin.mobs().spawn(def, l);
            if (e == null) {
                return false;
            }
            plugin.mobs().fillHealth(e);
            if (e instanceof Mob m) {
                m.setAI(false);
            }
            st = plugin.mobs().state(e);
            temporary = true;
            if (st == null) {
                return false;
            }
        }
        boolean ok = engine.executor().test(st, b, b.applyTo(base), viewer);
        if (temporary) {
            final MobState fs = st;
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.mobs().discard(fs), 240L);
        }
        return ok;
    }
}
