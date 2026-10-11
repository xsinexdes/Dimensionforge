package me.dimensionforge.mob;

import me.dimensionforge.ore.DropEntry;
import me.dimensionforge.skill.model.BossPhase;
import me.dimensionforge.skill.model.SkillBinding;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Definición de un mob custom (mobs.yml). */
public final class MobDef {

    public final String id;
    public String dimension = "";
    public boolean enabled = true;
    public String displayName;
    public String entityType = "ZOMBIE";

    // estadísticas (atributos reales)
    public double health = 40;
    public double damage = 6;
    public double speed = 0.23;
    public double armor = 0;
    public double toughness = 0;
    public double knockbackResistance = 0;
    public double followRange = 32;
    /** Escala real de la entidad (atributo GENERIC_SCALE: cambia también la hitbox). */
    public double scale = 1.0;
    public double regenPerSecond = 0;

    // comportamiento
    public boolean nameVisible = true;
    public boolean glowing = false;
    public boolean silentVanilla = false;
    public boolean fireImmune = false;

    // modelo (BetterModel)
    public String model = "";
    public double modelScale = 1.0;
    public String animIdle = "idle";
    public String animWalk = "walk";
    public String animAttack = "attack";
    public String animHurt = "hurt";
    public String animDeath = "death";
    public String animSpawn = "";

    // drops
    public List<DropEntry> drops = new ArrayList<>();
    public boolean replaceVanillaDrops = true;
    public int xpMin = 0;
    public int xpMax = 0;

    // sonidos y partículas
    public String ambientSound = "";
    public int ambientIntervalSeconds = 8;
    public String hurtSound = "";
    public String deathSound = "";
    public String particle = "";
    public int particleCount = 0;

    // barra de jefe
    public boolean bossBar = false;
    public String bossBarTitle = "";
    public String bossBarColor = "RED";
    public String bossBarStyle = "PROGRESS";
    public double bossBarRange = 48;

    // habilidades
    public boolean skillsEnabled = true;
    public List<SkillBinding> skills = new ArrayList<>();
    public List<BossPhase> phases = new ArrayList<>();

    public List<SpawnRule> spawnRules = new ArrayList<>();

    public MobDef(String id) {
        this.id = id;
        this.displayName = "<red>" + id;
    }

    public EntityType type() {
        try {
            return EntityType.valueOf(entityType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EntityType.ZOMBIE;
        }
    }

    public boolean hasModel() {
        return model != null && !model.isBlank();
    }

    public void save(ConfigurationSection s) {
        s.set("dimension", dimension);
        s.set("enabled", enabled);
        s.set("name", displayName);
        s.set("type", entityType);
        s.set("stats.health", health);
        s.set("stats.damage", damage);
        s.set("stats.speed", speed);
        s.set("stats.armor", armor);
        s.set("stats.toughness", toughness);
        s.set("stats.knockback-resistance", knockbackResistance);
        s.set("stats.follow-range", followRange);
        s.set("stats.scale", scale);
        s.set("stats.regen-per-second", regenPerSecond);
        s.set("behavior.name-visible", nameVisible);
        s.set("behavior.glowing", glowing);
        s.set("behavior.silent-vanilla", silentVanilla);
        s.set("behavior.fire-immune", fireImmune);
        s.set("model.name", model);
        s.set("model.scale", modelScale);
        s.set("model.anim.idle", animIdle);
        s.set("model.anim.walk", animWalk);
        s.set("model.anim.attack", animAttack);
        s.set("model.anim.hurt", animHurt);
        s.set("model.anim.death", animDeath);
        s.set("model.anim.spawn", animSpawn);
        DropEntry.saveList(s, "drops.items", drops);
        s.set("drops.replace-vanilla", replaceVanillaDrops);
        s.set("drops.xp-min", xpMin);
        s.set("drops.xp-max", xpMax);
        s.set("fx.ambient-sound", ambientSound);
        s.set("fx.ambient-interval", ambientIntervalSeconds);
        s.set("fx.hurt-sound", hurtSound);
        s.set("fx.death-sound", deathSound);
        s.set("fx.particle", particle);
        s.set("fx.particle-count", particleCount);
        s.set("bossbar.enabled", bossBar);
        s.set("bossbar.title", bossBarTitle);
        s.set("bossbar.color", bossBarColor);
        s.set("bossbar.style", bossBarStyle);
        s.set("bossbar.range", bossBarRange);
        s.set("skills-enabled", skillsEnabled);
        List<Map<String, Object>> sk = new ArrayList<>();
        for (SkillBinding b : skills) sk.add(b.toMap());
        s.set("skills", sk);
        List<Map<String, Object>> ph = new ArrayList<>();
        for (BossPhase p : phases) ph.add(p.toMap());
        s.set("phases", ph);
        s.set("spawn-rules", null);
        for (int i = 0; i < spawnRules.size(); i++) {
            spawnRules.get(i).save(s.createSection("spawn-rules." + i));
        }
    }

    public static MobDef load(String id, ConfigurationSection s) {
        MobDef m = new MobDef(id);
        m.dimension = s.getString("dimension", "");
        m.enabled = s.getBoolean("enabled", true);
        m.displayName = s.getString("name", m.displayName);
        m.entityType = s.getString("type", "ZOMBIE");
        m.health = s.getDouble("stats.health", 40);
        m.damage = s.getDouble("stats.damage", 6);
        m.speed = s.getDouble("stats.speed", 0.23);
        m.armor = s.getDouble("stats.armor", 0);
        m.toughness = s.getDouble("stats.toughness", 0);
        m.knockbackResistance = s.getDouble("stats.knockback-resistance", 0);
        m.followRange = s.getDouble("stats.follow-range", 32);
        m.scale = s.getDouble("stats.scale", 1.0);
        m.regenPerSecond = s.getDouble("stats.regen-per-second", 0);
        m.nameVisible = s.getBoolean("behavior.name-visible", true);
        m.glowing = s.getBoolean("behavior.glowing", false);
        m.silentVanilla = s.getBoolean("behavior.silent-vanilla", false);
        m.fireImmune = s.getBoolean("behavior.fire-immune", false);
        m.model = s.getString("model.name", "");
        m.modelScale = s.getDouble("model.scale", 1.0);
        m.animIdle = s.getString("model.anim.idle", "idle");
        m.animWalk = s.getString("model.anim.walk", "walk");
        m.animAttack = s.getString("model.anim.attack", "attack");
        m.animHurt = s.getString("model.anim.hurt", "hurt");
        m.animDeath = s.getString("model.anim.death", "death");
        m.animSpawn = s.getString("model.anim.spawn", "");
        m.drops = DropEntry.loadList(s, "drops.items");
        m.replaceVanillaDrops = s.getBoolean("drops.replace-vanilla", true);
        m.xpMin = s.getInt("drops.xp-min", 0);
        m.xpMax = s.getInt("drops.xp-max", 0);
        m.ambientSound = s.getString("fx.ambient-sound", "");
        m.ambientIntervalSeconds = Math.max(1, s.getInt("fx.ambient-interval", 8));
        m.hurtSound = s.getString("fx.hurt-sound", "");
        m.deathSound = s.getString("fx.death-sound", "");
        m.particle = s.getString("fx.particle", "");
        m.particleCount = s.getInt("fx.particle-count", 0);
        m.bossBar = s.getBoolean("bossbar.enabled", false);
        m.bossBarTitle = s.getString("bossbar.title", "");
        m.bossBarColor = s.getString("bossbar.color", "RED");
        m.bossBarStyle = s.getString("bossbar.style", "PROGRESS");
        m.bossBarRange = s.getDouble("bossbar.range", 48);
        m.skillsEnabled = s.getBoolean("skills-enabled", true);
        for (Map<?, ?> map : s.getMapList("skills")) m.skills.add(SkillBinding.fromMap(map));
        for (Map<?, ?> map : s.getMapList("phases")) m.phases.add(BossPhase.fromMap(map));
        m.phases.sort(java.util.Comparator.comparingInt((BossPhase p) -> p.healthPercent).reversed());
        ConfigurationSection sr = s.getConfigurationSection("spawn-rules");
        if (sr != null) {
            for (String k : sr.getKeys(false)) {
                ConfigurationSection rs = sr.getConfigurationSection(k);
                if (rs != null) m.spawnRules.add(SpawnRule.load(rs));
            }
        }
        return m;
    }
}
